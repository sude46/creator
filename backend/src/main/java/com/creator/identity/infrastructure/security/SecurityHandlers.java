package com.creator.identity.infrastructure.security;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.identity.application.AccountQueryService;
import com.creator.identity.web.UserView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

@Component
public class SecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler,
        AuthenticationSuccessHandler, AuthenticationFailureHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityHandlers.class);
    private final AccountQueryService accounts;
    private final SecurityResponseWriter writer;

    public SecurityHandlers(AccountQueryService accounts, SecurityResponseWriter writer) {
        this.accounts = accounts;
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        writer.error(response, ErrorCode.UNAUTHENTICATED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException exception) throws IOException {
        writer.error(response, exception instanceof CsrfException ? ErrorCode.CSRF_INVALID : ErrorCode.FORBIDDEN);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        try {
            writer.write(response, UserView.from(accounts.current(authentication.getName())));
        } catch (BusinessException exception) {
            invalidate(request);
            writer.error(response, exception.getCode());
        } catch (DataAccessException exception) {
            invalidate(request);
            LOGGER.error("Account lookup failed after authentication", exception);
            writer.error(response, ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        if (exception instanceof AuthenticationCapacityException) {
            response.setHeader("Retry-After", "1");
            writer.error(response, ErrorCode.AUTH_BUSY);
        } else if (exception instanceof AuthenticationServiceException) {
            LOGGER.error("Authentication service unavailable", exception);
            writer.error(response, ErrorCode.SERVICE_UNAVAILABLE);
        } else {
            writer.error(response, ErrorCode.LOGIN_FAILED);
        }
    }

    private void invalidate(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
