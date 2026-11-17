package com.creator.video.application.processing;

import com.creator.video.domain.*;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class DispatchTransactions {
    private final DispatchRepository repository;
    private final DispatchProperties properties;
    private final Clock clock;

    public DispatchTransactions(DispatchRepository repository, DispatchProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(timeout = 5)
    public Optional<DispatchClaim> claim(long taskId) {
        var now = LocalDateTime.now(clock);
        if (!repository.claim(taskId, UUID.randomUUID().toString(), now, now.plusSeconds(properties.leaseSeconds()))) {
            return Optional.empty();
        }
        return repository.claimed(taskId);
    }

    @Transactional(timeout = 5)
    public boolean complete(DispatchClaim claim, PublicationResult result) {
        var now = LocalDateTime.now(clock);
        if (result == PublicationResult.CONFIRMED) {
            return repository.markPublished(claim, now, now.plusSeconds(properties.redriveSeconds()));
        }
        return repository.markFailed(claim, now, now.plusSeconds(properties.retrySeconds(claim.dispatchAttempts())), result);
    }
}
