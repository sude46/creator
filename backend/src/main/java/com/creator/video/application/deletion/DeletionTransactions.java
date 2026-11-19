package com.creator.video.application.deletion;

import com.creator.video.domain.DeletionRepository;
import com.creator.video.domain.VideoDeletion;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

public class DeletionTransactions {
    public record CleanupClaim(long deletionId, long videoId, List<String> objectKeys) { }

    private final DeletionRepository deletions;
    private final Clock clock;

    public DeletionTransactions(DeletionRepository deletions, Clock clock) {
        this.deletions = deletions;
        this.clock = clock;
    }

    @Transactional(timeout = 5)
    public Optional<CleanupClaim> claim(VideoDeletion deletion) {
        var now = LocalDateTime.now(clock);
        if (!deletions.claim(deletion.id(), now, now.plusSeconds(120))) return Optional.empty();
        return Optional.of(new CleanupClaim(deletion.id(), deletion.videoId(), deletions.objectKeys(deletion.videoId())));
    }

    @Transactional(timeout = 5)
    public void complete(CleanupClaim claim) {
        deletions.finish(claim.deletionId(), claim.videoId(), LocalDateTime.now(clock));
    }

    @Transactional(timeout = 5)
    public void retry(long deletionId, int attempts) {
        long delay = Math.min(3600, 30L << Math.min(6, Math.max(0, attempts - 1)));
        deletions.retry(deletionId, LocalDateTime.now(clock).plusSeconds(delay), "STORAGE_UNAVAILABLE");
    }
}
