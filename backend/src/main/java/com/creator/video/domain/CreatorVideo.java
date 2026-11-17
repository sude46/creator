package com.creator.video.domain;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import java.time.LocalDateTime;

public record CreatorVideo(long id, long ownerId, long categoryId, String title, String description,
        long version, long processingGeneration, boolean sourceUploaded, boolean moderationBlocked,
        String processingStatus, String reviewStatus, String publicationStatus, String taskState,
        ProcessingFailure failureCode, int executionAttempts, LocalDateTime nextAttemptAt,
        Long durationMillis, Integer mediaWidth, Integer mediaHeight) {
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
}
