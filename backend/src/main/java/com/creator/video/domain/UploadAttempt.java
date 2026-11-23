package com.creator.video.domain;

import java.time.LocalDateTime;

public record UploadAttempt(String id, Long videoId, Long ownerId, String objectKey, UploadAttemptState state,
        LocalDateTime expiresAt, Long declaredSize, Integer chunkSize, String fingerprint, long receivedBytes) {
    public UploadAttempt(String id, Long videoId, Long ownerId, String objectKey, UploadAttemptState state,
            LocalDateTime expiresAt) {
        this(id, videoId, ownerId, objectKey, state, expiresAt, null, null, null, 0);
    }

    public boolean chunked() { return declaredSize != null && chunkSize != null; }

    public int chunkCount() {
        return chunked() ? (int) ((declaredSize + chunkSize - 1) / chunkSize) : 0;
    }
}
