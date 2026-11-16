package com.creator.video.infrastructure.upload;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("creator.storage")
public record StorageProperties(@NotBlank String endpoint, @NotBlank String accessKey,
        @NotBlank String secretKey, @Pattern(regexp = "[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]") String bucket,
        @Min(5) @Max(120) int operationSeconds) {
    @Override
    public String toString() {
        return "StorageProperties[credentials=REDACTED]";
    }
}
