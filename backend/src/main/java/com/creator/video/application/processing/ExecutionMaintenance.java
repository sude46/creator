package com.creator.video.application.processing;

import com.creator.video.domain.ExecutionRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExecutionMaintenance implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutionMaintenance.class);
    private final ExecutionRepository repository;
    private final ExecutionTransactions transactions;
    private final ProcessingStorage storage;
    private final ProcessingWorkspace workspace;
    private final Clock clock;

    public ExecutionMaintenance(ExecutionRepository repository, ExecutionTransactions transactions,
            ProcessingStorage storage, ProcessingWorkspace workspace, Clock clock) {
        this.repository = repository;
        this.transactions = transactions;
        this.storage = storage;
        this.workspace = workspace;
        this.clock = clock;
    }

    @Override
    public void run() {
        try {
            for (var message : repository.recoverable(LocalDateTime.now(clock), 10)) {
                try { transactions.recover(message); }
                catch (RuntimeException exception) { LOGGER.warn("Execution recovery deferred [taskId={}]", message.taskId()); }
            }
            for (var output : repository.expiredOutputs(LocalDateTime.now(clock), 10)) {
                try {
                    var claimed = transactions.claimCleanup(output.executionToken());
                    if (claimed.isPresent()) {
                        storage.delete(claimed.get().objectKey());
                        transactions.cleaned(output.executionToken());
                    }
                } catch (RuntimeException exception) { LOGGER.warn("Processing output cleanup deferred [taskId={}]", output.taskId()); }
            }
            workspace.cleanAbandoned();
        } catch (Exception exception) {
            LOGGER.warn("Execution maintenance unavailable; next scan will retry [failure={}]", exception.getClass().getSimpleName());
        }
    }
}
