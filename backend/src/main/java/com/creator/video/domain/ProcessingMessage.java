package com.creator.video.domain;

public record ProcessingMessage(int schemaVersion, long taskId, long videoId, long generation) {
    public ProcessingMessage {
        if (schemaVersion != 1 || taskId <= 0 || videoId <= 0 || generation <= 0) {
            throw new IllegalArgumentException("Invalid processing message");
        }
    }
}
