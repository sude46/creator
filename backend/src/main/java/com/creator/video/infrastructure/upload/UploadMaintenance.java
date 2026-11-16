package com.creator.video.infrastructure.upload;

import com.creator.video.application.ObjectStorage;
import com.creator.video.application.UploadStaging;
import com.creator.video.application.UploadTransactions;
import com.creator.video.domain.UploadRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public class UploadMaintenance {
    private static final Logger LOGGER = LoggerFactory.getLogger(UploadMaintenance.class);
    private final UploadRepository repository;
    private final UploadTransactions transactions;
    private final ObjectStorage storage;
    private final UploadStaging staging;
    private final Clock clock;

    public UploadMaintenance(UploadRepository repository, UploadTransactions transactions, ObjectStorage storage,
            UploadStaging staging, Clock clock) {
        this.repository = repository;
        this.transactions = transactions;
        this.storage = storage;
        this.staging = staging;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${creator.upload.cleanup-interval-ms:60000}", initialDelayString = "${creator.upload.cleanup-interval-ms:60000}")
    public void clean() {
        try {
            for (String id : repository.expiredAttempts(LocalDateTime.now(clock), 10)) {
                try {
                    var claim = transactions.claimCleanup(id);
                    if (claim.isPresent()) {
                        storage.delete(claim.get().objectKey());
                        transactions.finishCleanup(id);
                    }
                } catch (RuntimeException exception) {
                    LOGGER.warn("Upload object cleanup failed [attemptId={}, failure={}]; lease expiry permits retry",
                            id, exception.getClass().getSimpleName());
                }
            }
            staging.cleanAbandoned();
        } catch (Exception exception) {
            LOGGER.warn("Upload maintenance unavailable [failure={}]; next scheduled run will retry", exception.getClass().getSimpleName());
        }
    }
}
