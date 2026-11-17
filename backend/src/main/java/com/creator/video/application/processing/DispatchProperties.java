package com.creator.video.application.processing;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("creator.processing.dispatch")
public record DispatchProperties(@Min(1) @Max(50) int batchSize,
        @Min(1) @Max(10) int confirmSeconds, @Min(25) @Max(120) int leaseSeconds,
        @Min(60) @Max(3600) int redriveSeconds, @Min(30) @Max(600) int messageTtlSeconds,
        @Min(1) @Max(10000) int maxQueueMessages, @Min(1000) @Max(3600000) long scanMilliseconds,
        @Min(5) @Max(600) int retryMaxSeconds) {
    @AssertTrue(message = "投递租约必须覆盖连接和确认等待预算")
    public boolean isLeaseSafe() { return leaseSeconds >= confirmSeconds + 20; }
    @AssertTrue(message = "重新投递间隔必须覆盖消息 TTL 并保留余量")
    public boolean isRedriveSafe() { return redriveSeconds >= messageTtlSeconds + 30; }

    public long retrySeconds(long attempts) {
        return Math.min(retryMaxSeconds, 5L << (int) Math.min(7L, Math.max(0L, attempts - 1)));
    }
}
