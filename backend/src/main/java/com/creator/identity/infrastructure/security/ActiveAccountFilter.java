package com.creator.identity.infrastructure.security;

import com.creator.common.ErrorCode;
import com.creator.identity.application.AccountQueryService;
import com.creator.identity.domain.Actor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class ActiveAccountFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ActiveAccountFilter.class);
    private final AccountQueryService accounts;
    private final SecurityResponseWriter writer;
    private final DeviceSessionGuard devices;

    public ActiveAccountFilter(AccountQueryService accounts, SecurityResponseWriter writer, DeviceSessionGuard devices) {
        this.accounts = accounts;
        this.writer = writer;
        this.devices = devices;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            Optional<Actor> actor;
            try {
                actor = accounts.findActive(authentication.getName());
            } catch (DataAccessException exception) {
                LOGGER.error("Account availability check failed", exception);
                writer.error(response, ErrorCode.SERVICE_UNAVAILABLE);
                return;
            }
            boolean currentRole = actor.isPresent() && authentication.getAuthorities().stream()
                    .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + actor.get().role().name()));
            if (!currentRole) {
                SecurityContextHolder.clearContext();
                var session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                writer.error(response, ErrorCode.SESSION_INVALID);
                return;
            }
            var current = actor.orElseThrow();
            try {
                if (!devices.validate(current.id(), request)) {
                    SecurityContextHolder.clearContext();
                    var session = request.getSession(false);
                    if (session != null) session.invalidate();
                    writer.error(response, ErrorCode.SESSION_INVALID);
                    return;
                }
            } catch (DataAccessException exception) {
                LOGGER.error("Device session check failed", exception);
                writer.error(response, ErrorCode.SERVICE_UNAVAILABLE);
                return;
            }
            request.setAttribute("currentActor", current);
        }
        chain.doFilter(request, response);
    }
}
