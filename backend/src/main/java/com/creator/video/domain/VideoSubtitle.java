package com.creator.video.domain;

import java.time.LocalDateTime;

public record VideoSubtitle(long videoId, byte[] content, int sizeBytes, LocalDateTime updatedAt) {
}
