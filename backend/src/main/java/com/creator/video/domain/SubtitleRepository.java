package com.creator.video.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SubtitleRepository {
    record Metadata(long videoId, String language, String label, int sizeBytes, LocalDateTime updatedAt) { }
    Optional<VideoSubtitle> find(long videoId, String language);
    List<Metadata> list(long videoId);
    Optional<Metadata> metadata(long videoId, String language);
    void save(long videoId, String language, String label, byte[] content, int sizeBytes);
    void delete(long videoId, String language);
}
