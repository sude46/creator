package com.creator.video.domain;

import java.time.LocalDateTime;

public record UploadAttempt(String id, Long videoId, Long ownerId, String objectKey, UploadAttemptState state,
        LocalDateTime expiresAt) {
}
