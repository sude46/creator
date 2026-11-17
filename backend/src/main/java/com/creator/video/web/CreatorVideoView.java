package com.creator.video.web;

import com.creator.video.domain.CreatorVideo;
import java.time.LocalDateTime;

public record CreatorVideoView(String id, String categoryId, String title, String description, long version,
        long processingGeneration, boolean sourceUploaded, boolean moderationBlocked, String processingStatus,
        String reviewStatus, String publicationStatus, String taskState, String failureCode, int executionAttempts,
        LocalDateTime nextAttemptAt, boolean retryAllowed, Long durationMillis, Integer width, Integer height,
        long reviewRound, LocalDateTime reviewSubmittedAt, LocalDateTime publishedAt) {
    public static CreatorVideoView from(CreatorVideo video) {
        return new CreatorVideoView(Long.toString(video.id()), Long.toString(video.categoryId()), video.title(), video.description(),
                video.version(), video.processingGeneration(), video.sourceUploaded(), video.moderationBlocked(), video.processingStatus(),
                video.reviewStatus(), video.publicationStatus(), video.taskState(), video.failureCode() == null ? null : video.failureCode().name(),
                video.executionAttempts(), video.nextAttemptAt(), video.retryAllowed(), video.durationMillis(), video.mediaWidth(), video.mediaHeight(),
                video.reviewRound(), video.reviewSubmittedAt(), video.publishedAt());
    }
}
