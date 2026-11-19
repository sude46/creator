package com.creator.video.domain;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import java.time.LocalDateTime;

public record CreatorVideo(long id, long ownerId, long categoryId, String title, String description,
        long version, long processingGeneration, boolean sourceUploaded, boolean moderationBlocked,
        String processingStatus, String reviewStatus, String publicationStatus, String taskState,
        ProcessingFailure failureCode, int executionAttempts, LocalDateTime nextAttemptAt,
        Long durationMillis, Integer mediaWidth, Integer mediaHeight, long reviewRound,
        LocalDateTime reviewSubmittedAt, LocalDateTime publishedAt, String coverKey, String rejectionReason) {
    public WorkStatus workStatus() {
        if (moderationBlocked) return WorkStatus.BLOCKED;
        if (!sourceUploaded) return WorkStatus.NEEDS_UPLOAD;
        if ("PENDING".equals(processingStatus) || "PROCESSING".equals(processingStatus)) return WorkStatus.PROCESSING;
        if ("FAILED".equals(processingStatus)) return WorkStatus.FAILED;
        if ("PENDING".equals(reviewStatus)) return WorkStatus.PENDING_REVIEW;
        if ("REJECTED".equals(reviewStatus)) return WorkStatus.REJECTED;
        if ("APPROVED".equals(reviewStatus) && "PUBLISHED".equals(publicationStatus)) return WorkStatus.PUBLISHED;
        if ("APPROVED".equals(reviewStatus) && "OFFLINE".equals(publicationStatus)) return WorkStatus.TAKEN_DOWN;
        return WorkStatus.READY_TO_SUBMIT;
    }

    public String statusReason() {
        return switch (workStatus()) {
            case REJECTED -> rejectionReason == null || rejectionReason.isBlank() ? "未通过审核" : rejectionReason;
            case FAILED -> failureReason();
            default -> null;
        };
    }

    private String failureReason() {
        if (failureCode == null) return "视频处理失败";
        return switch (failureCode) {
            case INVALID_MEDIA -> "文件损坏或媒体信息无效";
            case UNSUPPORTED_MEDIA -> "编码、时长或分辨率不符合支持范围";
            case STORAGE_UNAVAILABLE -> "存储暂时不可用";
            case PROCESS_FAILED -> "视频处理失败";
            case PROCESS_TIMEOUT -> "视频处理超时";
            case TEMP_UNAVAILABLE -> "处理空间暂时不足";
            case LEASE_EXPIRED -> "处理任务执行超时";
        };
    }

    public boolean retryAllowed() {
        boolean failed = "FAILED".equals(taskState) && failureCode != null && failureCode.retryable();
        boolean cancelled = "CANCELLED".equals(taskState);
        return sourceUploaded && !moderationBlocked && processingStatus.equals("FAILED") && (failed || cancelled)
                && reviewStatus.equals("NOT_SUBMITTED") && publicationStatus.equals("UNPUBLISHED")
                && processingGeneration < Long.MAX_VALUE;
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
