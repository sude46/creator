package com.creator.video.application;

import com.creator.audit.application.AuditLog;
import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.identity.domain.Actor;
import com.creator.video.domain.*;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class UploadTransactions {
    private final UploadRepository repository;
    private final AuditLog audit;
    private final UploadProperties properties;
    private final Clock clock;

    public UploadTransactions(UploadRepository repository, AuditLog audit, UploadProperties properties, Clock clock) {
        this.repository = repository;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UploadVideo status(Actor actor, long videoId) {
        return owned(actor, videoId, false);
    }

    @Transactional(timeout = 5)
    public UploadAttempt reserve(Actor actor, long videoId, long version) {
        if (!repository.lockActiveOwner(actor)) throw new BusinessException(ErrorCode.SESSION_INVALID);
        requireWritable(owned(actor, videoId, true), version);
        requireQuota(actor.id(), 0);
        if (repository.activeAttempts(actor.id()) >= properties.maxPending()) throw new BusinessException(ErrorCode.UPLOAD_BUSY);
        String id = UUID.randomUUID().toString();
        var attempt = new UploadAttempt(id, videoId, actor.id(), "sources/" + id + ".mp4", UploadAttemptState.UPLOADING,
                now().plusSeconds(properties.attemptSeconds()));
        repository.insertAttempt(attempt);
        return attempt;
    }

    @Transactional(timeout = 5)
    public UploadVideo bind(Actor actor, UploadAttempt attempt, long size, long version) {
        if (size <= 0 || size > properties.maxBytes()) throw new BusinessException(ErrorCode.INVALID_MEDIA);
        if (!repository.lockActiveOwner(actor)) throw new BusinessException(ErrorCode.SESSION_INVALID);
        requireWritable(owned(actor, attempt.videoId(), true), version);
        var current = repository.lockAttempt(attempt.id()).orElseThrow();
        if (!current.ownerId().equals(actor.id()) || !current.videoId().equals(attempt.videoId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (current.state() != UploadAttemptState.UPLOADING || !current.expiresAt().isAfter(now())) {
            throw new BusinessException(ErrorCode.UPLOAD_TIMEOUT);
        }
        requireQuota(actor.id(), size);
        repository.bind(current, size, version);
        repository.setAttemptState(current.id(), UploadAttemptState.BOUND, now());
        audit.record(actor, "SOURCE_UPLOADED", "VIDEO", current.videoId());
        return owned(actor, current.videoId(), true);
    }

    @Transactional(timeout = 5)
    public Optional<UploadAttempt> claimCleanup(String id) {
        var attempt = repository.lockAttempt(id).orElse(null);
        if (attempt == null || attempt.state() == UploadAttemptState.BOUND || attempt.state() == UploadAttemptState.DELETED
                || attempt.expiresAt().isAfter(now())) return Optional.empty();
        if (repository.hasReference(attempt.objectKey())) {
            repository.setAttemptState(id, UploadAttemptState.BOUND, now());
            return Optional.empty();
        }
        repository.setAttemptState(id, UploadAttemptState.CLEANING, now().plusSeconds(180));
        return Optional.of(attempt);
    }

    @Transactional(timeout = 5)
    public void finishCleanup(String id) {
        var attempt = repository.lockAttempt(id).orElseThrow();
        if (attempt.state() == UploadAttemptState.CLEANING) repository.setAttemptState(id, UploadAttemptState.DELETED, now());
    }

    private UploadVideo owned(Actor actor, long videoId, boolean lock) {
        return repository.findOwned(actor.id(), videoId, lock).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private void requireWritable(UploadVideo video, long version) {
        if (video.sourceAssetId() != null) throw new BusinessException(ErrorCode.SOURCE_ALREADY_BOUND);
        if (!video.writable()) throw new BusinessException(ErrorCode.NOT_FOUND);
        if (version < 0 || video.version() != version) throw new BusinessException(ErrorCode.STALE_WRITE);
    }

    private void requireQuota(long ownerId, long addedBytes) {
        if (repository.usedBytes(ownerId) > properties.userQuotaBytes() - addedBytes
                || repository.pendingCount(ownerId) >= properties.maxPending()) {
            throw new BusinessException(ErrorCode.UPLOAD_QUOTA_EXCEEDED);
        }
    }

    private LocalDateTime now() { return LocalDateTime.now(clock); }
}
