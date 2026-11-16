package com.creator.identity.infrastructure.security;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("creator.security.auth-work")
public record AuthWorkProperties(
        @Min(1) @Max(32) int maxConcurrent,
        @Min(10) @Max(14) int bcryptStrength) {
}
