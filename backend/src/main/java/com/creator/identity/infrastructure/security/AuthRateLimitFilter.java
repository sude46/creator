package com.creator.identity.infrastructure.security;

import com.creator.common.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

public class AuthRateLimitFilter extends OncePerRequestFilter {
    private final AuthRateLimiter limiter;
    private final SecurityResponseWriter writer;
    private final AuthRateLimitProperties properties;

    public AuthRateLimitFilter(AuthRateLimiter limiter, SecurityResponseWriter writer,
            AuthRateLimitProperties properties) {
        this.limiter = limiter;
        this.writer = writer;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String path = request.getServletPath();
        if (path.isEmpty()) {
            path = request.getRequestURI().substring(request.getContextPath().length());
        }
        boolean authAttempt = "POST".equals(request.getMethod())
                && ("/api/auth/login".equals(path) || "/api/auth/register".equals(path));
        if (authAttempt && !limiter.allow(request.getRemoteAddr())) {
            response.setHeader("Retry-After", Long.toString(properties.windowSeconds()));
            writer.error(response, ErrorCode.AUTH_RATE_LIMITED);
            return;
        }
        chain.doFilter(request, response);
    }

}
