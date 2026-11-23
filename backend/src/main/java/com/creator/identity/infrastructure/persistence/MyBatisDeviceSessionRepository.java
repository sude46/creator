package com.creator.identity.infrastructure.persistence;

import com.creator.identity.domain.DeviceSession;
import com.creator.identity.domain.DeviceSessionRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisDeviceSessionRepository implements DeviceSessionRepository {
    private final DeviceSessionMapper mapper;
    public MyBatisDeviceSessionRepository(DeviceSessionMapper mapper) { this.mapper = mapper; }
    @Override public Optional<DeviceSession> find(String id) { return Optional.ofNullable(mapper.find(id)); }
    @Override public List<DeviceSession> active(long userId, LocalDateTime now) { return mapper.active(userId, now); }
    @Override public void insert(DeviceSession session) { mapper.insert(session); }
    @Override public void touch(String id, LocalDateTime lastSeenAt, LocalDateTime expiresAt) {
        mapper.touch(id, lastSeenAt, expiresAt);
    }
    @Override public int revoke(String id, long userId, LocalDateTime now) { return mapper.revoke(id, userId, now); }
    @Override public int revokeOthers(long userId, String exceptId, LocalDateTime now) {
        return mapper.revokeOthers(userId, exceptId, now);
    }
    @Override public int revokeAll(long userId, LocalDateTime now) { return mapper.revokeAll(userId, now); }
    @Override public void delete(String id) { mapper.delete(id); }
}
