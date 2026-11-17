package com.creator.video.domain;

public record MediaMetadata(long durationMillis, int width, int height) {
    public MediaMetadata {
        if (durationMillis < 1 || durationMillis > 600000 || width < 1 || width > 1920 || height < 1 || height > 1920
                || (long) width * height > 1920L * 1080) throw new IllegalArgumentException("Media exceeds supported limits");
    }
}
