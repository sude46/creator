package com.creator.video.application.processing;

import com.creator.video.domain.DispatchRepository;
import com.creator.video.domain.PublicationResult;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DispatchService implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DispatchService.class);
    private final DispatchRepository repository;
    private final DispatchTransactions transactions;
    private final TaskPublisher publisher;
    private final DispatchProperties properties;
    private final Clock clock;

    public DispatchService(DispatchRepository repository, DispatchTransactions transactions, TaskPublisher publisher,
            DispatchProperties properties, Clock clock) {
        this.repository = repository;
        this.transactions = transactions;
        this.publisher = publisher;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void run() {
        try {
            for (Long taskId : repository.dueIds(LocalDateTime.now(clock), properties.batchSize())) {
                if (Thread.currentThread().isInterrupted()) return;
                try {
                    var claim = transactions.claim(taskId);
                    if (claim.isEmpty()) continue;
                    var result = publisher.publish(claim.get());
                    transactions.complete(claim.get(), result);
                    if (result != PublicationResult.CONFIRMED) {
                        LOGGER.warn("Processing task publication deferred [taskId={}, result={}]", taskId, result);
                    }
                } catch (RuntimeException exception) {
                    LOGGER.warn("Processing task dispatch failed [taskId={}, failure={}]; database lease permits recovery",
                            taskId, exception.getClass().getSimpleName());
                }
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Processing dispatch scan unavailable [failure={}]; next scan will retry", exception.getClass().getSimpleName());
        }
    }
}
