package com.creator.video.domain;

import java.time.LocalDateTime;

public record ContentReport(long id, long reporterId, String reporterName, String targetType, long targetId,
        String reason, String status, Long resolverId, String resolutionReason, LocalDateTime createdAt,
        LocalDateTime resolvedAt) { }
