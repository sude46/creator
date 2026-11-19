package com.creator.video.application.deletion;

import com.creator.video.application.ObjectStorage;
import com.creator.video.domain.DeletionRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public class VideoDeletionMaintenance {
    private static final Logger LOGGER = LoggerFactory.getLogger(VideoDeletionMaintenance.class);
    private final DeletionRepository deletions;
    private final DeletionTransactions transactions;
    private final ObjectStorage storage;
    private final Clock clock;

    public VideoDeletionMaintenance(DeletionRepository deletions, DeletionTransactions transactions,
            ObjectStorage storage, Clock clock) {
        this.deletions = deletions;
        this.transactions = transactions;
        this.storage = storage;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${creator.deletion.cleanup-interval-ms:60000}",
            initialDelayString = "${creator.deletion.cleanup-interval-ms:60000}")
    public void clean() {
        try {
            for (var deletion : deletions.due(LocalDateTime.now(clock), 10)) {
                var claimed = transactions.claim(deletion);
                if (claimed.isEmpty()) continue;
                var claim = claimed.get();
                try {
                    for (String key : claim.objectKeys()) storage.delete(key);
                    transactions.complete(claim);
                } catch (RuntimeException exception) {
                    transactions.retry(deletion.id(), deletion.attempts() + 1);
                    LOGGER.warn("Video deletion cleanup deferred [deletionId={}, videoId={}, failure={}]",
                            deletion.id(), deletion.videoId(), exception.getClass().getSimpleName());
                }
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Video deletion scan unavailable [failure={}]; next scan will retry",
                    exception.getClass().getSimpleName());
        }
    }
}
