package com.creator.video.domain;

import java.time.LocalDateTime;

public record ContentAppeal(long id, long appellantId, String appellantName, String targetType, long targetId,
        String targetTitle, String reason, String status, Long resolverId, String resolutionReason,
        LocalDateTime createdAt, LocalDateTime resolvedAt) { }
