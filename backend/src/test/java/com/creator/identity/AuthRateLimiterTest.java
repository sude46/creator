package com.creator.identity;

import com.creator.identity.infrastructure.security.AuthRateLimiter;
import com.creator.identity.infrastructure.security.AuthRateLimitProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthRateLimiterTest {
    @Test
    void limitIsPerAddressAndExpiresWithoutUnboundedRetention() {
        var millis = new AtomicLong(1_000);
        Clock clock = new Clock() {
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) { return this; }
            public Instant instant() { return Instant.ofEpochMilli(millis.get()); }
        };
        var limiter = new AuthRateLimiter(clock, new AuthRateLimitProperties(60, 2, 2));
        assertTrue(limiter.allow("192.0.2.1"));
        assertTrue(limiter.allow("192.0.2.1"));
        assertFalse(limiter.allow("192.0.2.1"));
        millis.addAndGet(1_000);
        assertTrue(limiter.allow("192.0.2.2"));
        assertFalse(limiter.allow("192.0.2.3"));
        millis.addAndGet(59_000);
        assertTrue(limiter.allow("192.0.2.3"));
        assertTrue(limiter.allow("192.0.2.2"));
        assertFalse(limiter.allow("192.0.2.2"));
        millis.addAndGet(1_000);
        assertTrue(limiter.allow("192.0.2.2"));
    }
}
