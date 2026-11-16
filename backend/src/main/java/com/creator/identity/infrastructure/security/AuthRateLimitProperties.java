package com.creator.identity.infrastructure.security;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("creator.security.auth-rate-limit")
public record AuthRateLimitProperties(
        @Min(1) @Max(3600) long windowSeconds,
        @Min(1) @Max(10000) int maxAttempts,
        @Min(1) @Max(100000) int maxEntries) {
}
