package com.creator.identity.infrastructure.security;

import org.springframework.security.core.AuthenticationException;

public class AuthenticationCapacityException extends AuthenticationException {
    public AuthenticationCapacityException() {
        super("Authentication capacity exceeded");
    }
}
