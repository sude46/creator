package com.creator.video.domain;

import java.time.LocalDateTime;

public record VideoDeletion(long id, long videoId, long ownerId, String state, int attempts,
        LocalDateTime nextAttemptAt, String lastError) {
    public static final String PENDING = "PENDING";
    public static final String CLEANING = "CLEANING";
    public static final String DONE = "DONE";
}
