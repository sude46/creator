package com.creator.video.infrastructure.media;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("creator.media")
public record MediaProperties(@NotBlank String publicEndpoint, @NotBlank String region,
        @Min(15) @Max(120) int linkSeconds, @Min(30) @Max(900) int coverLinkSeconds) { }
