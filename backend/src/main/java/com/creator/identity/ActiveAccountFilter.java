package com.creator.identity;

import com.creator.common.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class ActiveAccountFilter extends OncePerRequestFilter {
    private final UserAccountMapper accounts;
    private final ObjectMapper json;

    public ActiveAccountFilter(UserAccountMapper accounts, ObjectMapper json) {
        this.accounts = accounts;
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            var account = accounts.findByUsername(authentication.getName());
            boolean currentRole = account != null && authentication.getAuthorities().stream()
                    .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + account.getRole()));
            if (account == null || !"ACTIVE".equals(account.getStatus()) || !currentRole) {
                SecurityContextHolder.clearContext();
                var session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                json.writeValue(response.getOutputStream(), new ApiError("SESSION_INVALID", "账号状态已变更，请重新登录"));
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
