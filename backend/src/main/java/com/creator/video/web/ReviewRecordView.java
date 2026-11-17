package com.creator.video.web;

import com.creator.video.domain.ReviewRecord;
import java.time.LocalDateTime;

public record ReviewRecordView(String id, String videoId, long reviewRound, long contentVersion, long processingGeneration,
        String reviewerId, String outcome, String reason, String title, String description, String categoryId, LocalDateTime createdAt) {
    public static ReviewRecordView from(ReviewRecord record) {
        return new ReviewRecordView(Long.toString(record.id()), Long.toString(record.videoId()), record.reviewRound(),
                record.contentVersion(), record.processingGeneration(), Long.toString(record.reviewerId()), record.outcome().name(),
                record.reason(), record.title(), record.description(), Long.toString(record.categoryId()), record.createdAt());
    }
}
