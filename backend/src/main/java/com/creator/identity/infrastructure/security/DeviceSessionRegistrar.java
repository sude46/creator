package com.creator.identity.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;

public interface DeviceSessionRegistrar {
    void register(long userId, HttpServletRequest request);
}
