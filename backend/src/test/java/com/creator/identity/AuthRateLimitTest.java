package com.creator.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthRateLimitTest {
    @Test
    void limitIsPerAddressAndExpiresWithoutUnboundedRetention() {
        var millis = new AtomicLong(1_000);
        Clock clock = new Clock() {
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) { return this; }
            public Instant instant() { return Instant.ofEpochMilli(millis.get()); }
        };
        var limiter = new AuthRateLimitFilter(new ObjectMapper(), clock);
        for (int attempt = 0; attempt < 20; attempt++) {
            assertTrue(limiter.allow("192.0.2.1"));
        }
        assertFalse(limiter.allow("192.0.2.1"));
        assertTrue(limiter.allow("192.0.2.2"));
        millis.addAndGet(60_000);
        assertTrue(limiter.allow("192.0.2.1"));
    }
}
