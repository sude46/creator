package com.creator.identity;

import com.creator.common.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.filter.OncePerRequestFilter;

public class AuthRateLimitFilter extends OncePerRequestFilter {
    private final Map<String, Window> windows = new HashMap<>();
    private final ObjectMapper json;
    private final Clock clock;

    public AuthRateLimitFilter(ObjectMapper json, Clock clock) {
        this.json = json;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        boolean authAttempt = "POST".equals(request.getMethod())
                && ("/api/auth/login".equals(request.getServletPath())
                || "/api/auth/register".equals(request.getServletPath()));
        if (authAttempt && !allow(request.getRemoteAddr())) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json;charset=UTF-8");
            json.writeValue(response.getOutputStream(), new ApiError("AUTH_RATE_LIMITED", "请求过于频繁，请一分钟后再试"));
            return;
        }
        chain.doFilter(request, response);
    }

    synchronized boolean allow(String address) {
        long now = clock.millis();
        windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt() >= 60_000);
        var window = windows.get(address);
        if (window == null) {
            if (windows.size() >= 10_000) {
                return false;
            }
            windows.put(address, new Window(now, 1));
            return true;
        }
        if (window.count() >= 20) {
            return false;
        }
        windows.put(address, new Window(window.startedAt(), window.count() + 1));
        return true;
    }

    private record Window(long startedAt, int count) {
    }
}
