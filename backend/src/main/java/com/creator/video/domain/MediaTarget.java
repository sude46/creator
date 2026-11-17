package com.creator.video.domain;

public record MediaTarget(String objectKey, long sizeBytes) {
    @Override
    public String toString() { return "MediaTarget[objectKey=REDACTED]"; }
}
