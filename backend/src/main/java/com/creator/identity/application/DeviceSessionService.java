package com.creator.identity.application;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.identity.domain.AccountRepository;
import com.creator.identity.domain.DeviceSession;
import com.creator.identity.domain.DeviceSessionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceSessionService {
    public static final Duration SESSION_TTL = Duration.ofMinutes(30);
    private static final Duration TOUCH_INTERVAL = Duration.ofSeconds(60);
    private final DeviceSessionRepository sessions;
    private final AccountRepository accounts;
    private final Clock clock;

    public DeviceSessionService(DeviceSessionRepository sessions, AccountRepository accounts, Clock clock) {
        this.sessions = sessions;
        this.accounts = accounts;
        this.clock = clock;
    }

    public DeviceSession register(long userId, String rawSessionId, String userAgent, String ip) {
        var now = now();
        long version = accounts.sessionVersion(userId);
        var session = new DeviceSession(UUID.randomUUID().toString(), userId, hash(rawSessionId), version,
                truncate(ip, 45), truncate(userAgent, 512), now, now, now.plus(SESSION_TTL), null);
        sessions.insert(session);
        return session;
    }

    public boolean validate(long userId, String id, String rawSessionId) {
        var session = sessions.find(id).orElse(null);
        if (session == null || session.userId() != userId || session.revokedAt() != null
                || !session.expiresAt().isAfter(now()) || !session.sessionHash().equals(hash(rawSessionId))) {
            return false;
        }
        if (session.sessionVersion() != accounts.sessionVersion(userId)) return false;
        var now = now();
        if (session.lastSeenAt().isBefore(now.minus(TOUCH_INTERVAL))) {
            sessions.touch(id, now, now.plus(SESSION_TTL));
        }
        return true;
    }

    public List<DeviceSession> list(long userId) {
        return sessions.active(userId, now());
    }

    public void revoke(long userId, String id) {
        if (id == null || id.isBlank() || sessions.revoke(id, userId, now()) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }

    public int revokeOthers(long userId, String currentId) {
        if (currentId == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        return sessions.revokeOthers(userId, currentId, now());
    }

    @Transactional(timeout = 5)
    public void revokeAll(long userId) {
        if (!accounts.incrementSessionVersion(userId)) throw new BusinessException(ErrorCode.STALE_WRITE);
        sessions.revokeAll(userId, now());
    }

    public void logout(String id) {
        if (id != null && !id.isBlank()) sessions.delete(id);
    }

    private String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private String truncate(String value, int max) {
        if (value == null || value.isBlank()) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
