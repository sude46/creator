package com.creator.video.application.processing;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("creator.processing.worker")
public record WorkerProperties(@NotBlank String tempDirectory, @NotBlank String ffprobe, @NotBlank String ffmpeg,
        @Min(1) @Max(3) int maxAttempts, @Min(90) @Max(600) int taskSeconds,
        @Min(150) @Max(900) int leaseSeconds, @Min(1) @Max(30) int probeSeconds,
        @Min(1) @Max(60) int screenshotSeconds, @Min(1024) @Max(209715200) long maxSourceBytes,
        @Min(1048576) long minFreeBytes) {
    @AssertTrue(message = "执行租约须覆盖任务预算并保留至少60秒余量")
    public boolean isLeaseSafe() { return leaseSeconds >= taskSeconds + 60; }
}
