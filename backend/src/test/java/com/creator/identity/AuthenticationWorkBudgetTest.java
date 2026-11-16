package com.creator.identity;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.identity.application.AuthenticationWorkBudget;
import com.creator.identity.infrastructure.security.BoundedAuthenticationProvider;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticationWorkBudgetTest {
    @Test
    void rejectedAndFailedWorkDoesNotLeakPermits() {
        var budget = new AuthenticationWorkBudget(1);
        budget.execute(() -> {
            var error = assertThrows(BusinessException.class, () -> budget.execute(() -> "unexpected"));
            assertEquals(ErrorCode.AUTH_BUSY, error.getCode());
            return "held";
        });
        assertThrows(IllegalStateException.class, () -> budget.execute(() -> {
            throw new IllegalStateException("failed operation");
        }));
        assertEquals("available", budget.execute(() -> "available"));
    }

    @Test
    void oversizedCredentialsAreRejectedBeforeAccountLookupAndHashing() {
        var calls = new AtomicInteger();
        var provider = new BoundedAuthenticationProvider(new AuthenticationProvider() {
            public Authentication authenticate(Authentication authentication) {
                calls.incrementAndGet();
                return authentication;
            }

            public boolean supports(Class<?> authentication) {
                return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
            }
        }, new AuthenticationWorkBudget(1));
        assertThrows(BadCredentialsException.class, () -> provider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("author_one", "x".repeat(65))));
        assertThrows(BadCredentialsException.class, () -> provider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("author_one", "汉".repeat(25))));
        assertThrows(BadCredentialsException.class, () -> provider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("x".repeat(33), "SafePassword123")));
        assertEquals(0, calls.get());
        provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("author_one", "SafePassword123"));
        assertEquals(1, calls.get());
    }
}
