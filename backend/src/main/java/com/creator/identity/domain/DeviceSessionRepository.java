package com.creator.identity.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DeviceSessionRepository {
    Optional<DeviceSession> find(String id);
    List<DeviceSession> active(long userId, LocalDateTime now);
    void insert(DeviceSession session);
    void touch(String id, LocalDateTime lastSeenAt, LocalDateTime expiresAt);
    int revoke(String id, long userId, LocalDateTime now);
    int revokeOthers(long userId, String exceptId, LocalDateTime now);
    int revokeAll(long userId, LocalDateTime now);
    void delete(String id);
}
