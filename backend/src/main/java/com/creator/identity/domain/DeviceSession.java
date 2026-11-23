package com.creator.identity.domain;

import java.time.LocalDateTime;

public record DeviceSession(String id, long userId, String sessionHash, long sessionVersion, String ip,
        String userAgent, LocalDateTime createdAt, LocalDateTime lastSeenAt, LocalDateTime expiresAt,
        LocalDateTime revokedAt) {
}
