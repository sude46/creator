package com.creator.audit.domain;

import java.time.LocalDateTime;

public record AuditRecord(long id, long actorId, String action, String targetType, long targetId,
        String reason, String requestId, LocalDateTime createdAt) {
}
