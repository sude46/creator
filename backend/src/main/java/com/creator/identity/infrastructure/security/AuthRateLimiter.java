package com.creator.identity.infrastructure.security;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

public class AuthRateLimiter {
    private final Map<String, Window> windows = new LinkedHashMap<>();
    private final Clock clock;
    private final AuthRateLimitProperties properties;
    private long lastObservedTime = Long.MIN_VALUE;

    public AuthRateLimiter(Clock clock, AuthRateLimitProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    public synchronized boolean allow(String address) {
        long now = Math.max(clock.millis(), lastObservedTime);
        lastObservedTime = now;
        var iterator = windows.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (now - entry.getValue().startedAt() < properties.windowSeconds() * 1000) {
                break;
            }
            iterator.remove();
        }
        var window = windows.get(address);
        if (window == null) {
            if (windows.size() >= properties.maxEntries()) {
                return false;
            }
            windows.put(address, new Window(now, 1));
            return true;
        }
        if (window.count() >= properties.maxAttempts()) {
            return false;
        }
        windows.put(address, new Window(window.startedAt(), window.count() + 1));
        return true;
    }

    private record Window(long startedAt, int count) {
    }
}
