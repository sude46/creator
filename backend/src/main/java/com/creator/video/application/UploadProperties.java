package com.creator.video.application;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("creator.upload")
public record UploadProperties(@Min(1) @Max(4) int maxConcurrent,
        @Min(1024) @Max(209715200) long maxBytes, @NotBlank String tempDirectory,
        @Min(0) long minFreeBytes, @Min(1) @Max(300) int receiveSeconds,
        @Min(1) long userQuotaBytes, @Min(1) @Max(20) int maxPending,
        @Min(300) @Max(3600) int attemptSeconds,
        @Min(256) @Max(67108864) int chunkBytes) {
    @AssertTrue(message = "上传凭据有效期必须覆盖接收、存储超时与清理安全余量")
    public boolean isAttemptWindowSafe() { return attemptSeconds >= receiveSeconds + 240; }

    @AssertTrue(message = "分片数量不得超过 4096")
    public boolean isChunkPlanSane() { return (maxBytes + chunkBytes - 1) / chunkBytes <= 4096; }
}
