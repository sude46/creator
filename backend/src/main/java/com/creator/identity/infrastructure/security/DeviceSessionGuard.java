package com.creator.identity.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;

public interface DeviceSessionGuard {
    boolean validate(long userId, HttpServletRequest request);
}
