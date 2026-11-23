package com.creator.video.domain;

import java.time.LocalDateTime;
import java.util.Optional;

public interface SubtitleRepository {
    record Metadata(long videoId, int sizeBytes, LocalDateTime updatedAt) { }
    Optional<VideoSubtitle> find(long videoId);
    Optional<Metadata> metadata(long videoId);
    void save(long videoId, byte[] content, int sizeBytes);
    void delete(long videoId);
}
