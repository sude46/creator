package com.creator.video.domain;

import java.time.LocalDateTime;

public record VideoSubtitle(long videoId, String language, String label, byte[] content, int sizeBytes,
        LocalDateTime updatedAt) {
}
