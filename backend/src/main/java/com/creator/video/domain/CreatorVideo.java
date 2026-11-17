package com.creator.video.domain;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import java.time.LocalDateTime;

public record CreatorVideo(long id, long ownerId, long categoryId, String title, String description,
        long version, long processingGeneration, boolean sourceUploaded, boolean moderationBlocked,
        String processingStatus, String reviewStatus, String publicationStatus, String taskState,
        ProcessingFailure failureCode, int executionAttempts, LocalDateTime nextAttemptAt,
        Long durationMillis, Integer mediaWidth, Integer mediaHeight, long reviewRound,
        LocalDateTime reviewSubmittedAt, LocalDateTime publishedAt) {
    public boolean retryAllowed() {
        return sourceUploaded && !moderationBlocked && processingStatus.equals("FAILED") && "FAILED".equals(taskState)
                && failureCode != null && failureCode.retryable() && reviewStatus.equals("NOT_SUBMITTED")
                && publicationStatus.equals("UNPUBLISHED") && processingGeneration < Long.MAX_VALUE;
    }

    public void requireVersion(long expected) {
        if (expected < 0 || version != expected) throw new BusinessException(ErrorCode.STALE_WRITE);
    }

    public void requireRetryAllowed() {
        if (!retryAllowed()) throw new BusinessException(ErrorCode.PROCESSING_RETRY_NOT_ALLOWED);
    }

    public void requireReady() {
        if (moderationBlocked || !processed()) {
            throw new BusinessException(ErrorCode.REVIEW_STATE_CONFLICT);
        }
    }

    public void requireOffline() {
        if (!processed() || moderationBlocked || !reviewStatus.equals("APPROVED") || !publicationStatus.equals("PUBLISHED")) {
            throw new BusinessException(ErrorCode.PUBLICATION_STATE_CONFLICT);
        }
    }

    public void requirePublish() {
        if (!processed() || moderationBlocked || !reviewStatus.equals("APPROVED") || !publicationStatus.equals("OFFLINE")) {
            throw new BusinessException(ErrorCode.PUBLICATION_STATE_CONFLICT);
        }
    }

    public void requireModeration(boolean expectedBlocked) {
        if (!processed() || moderationBlocked != expectedBlocked) {
            throw new BusinessException(ErrorCode.PUBLICATION_STATE_CONFLICT);
        }
    }

    private boolean processed() {
        return sourceUploaded && processingStatus.equals("READY") && "SUCCEEDED".equals(taskState)
                && durationMillis != null && mediaWidth != null && mediaHeight != null;
    }

    public void requireEditable() {
        requireReady();
        if (reviewStatus.equals("PENDING")) throw new BusinessException(ErrorCode.REVIEW_STATE_CONFLICT);
    }

    public void requireSubmittable() {
        requireReady();
        if (!(reviewStatus.equals("NOT_SUBMITTED") || reviewStatus.equals("REJECTED")) || reviewRound == Long.MAX_VALUE
                || publicationStatus.equals("PUBLISHED")) throw new BusinessException(ErrorCode.REVIEW_STATE_CONFLICT);
    }

    public void requirePending(long expectedRound) {
        requireReady();
        if (!reviewStatus.equals("PENDING") || reviewRound != expectedRound || expectedRound < 1) {
            throw new BusinessException(ErrorCode.REVIEW_STATE_CONFLICT);
        }
    }
}
