package com.creator.identity.infrastructure.security;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.identity.application.AuthenticationWorkBudget;
import com.creator.identity.domain.PasswordPolicy;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;

public class BoundedAuthenticationProvider implements AuthenticationProvider {
    private final AuthenticationProvider delegate;
    private final AuthenticationWorkBudget budget;

    public BoundedAuthenticationProvider(AuthenticationProvider delegate, AuthenticationWorkBudget budget) {
        this.delegate = delegate;
        this.budget = budget;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        if (!(authentication.getCredentials() instanceof String password)
                || !PasswordPolicy.withinEncodingLimits(password)
                || authentication.getName().length() > 32) {
            throw new BadCredentialsException("Invalid credentials");
        }
        try {
            return budget.execute(() -> delegate.authenticate(authentication));
        } catch (BusinessException exception) {
            if (exception.getCode() == ErrorCode.AUTH_BUSY) {
                throw new AuthenticationCapacityException();
            }
            throw exception;
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return delegate.supports(authentication);
    }
}
