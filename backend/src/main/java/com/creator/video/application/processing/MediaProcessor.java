package com.creator.video.application.processing;

import com.creator.video.domain.MediaMetadata;
import java.nio.file.Path;

public interface MediaProcessor {
    void verify();
    MediaMetadata process(Path source, Path cover, long deadlineNanos);

    default ProcessedMedia processWithHls(Path source, Path cover, Path hlsDirectory, long deadlineNanos) {
        return new ProcessedMedia(process(source, cover, deadlineNanos), java.util.List.of());
    }

    default java.util.List<ProcessedMedia.HlsVariant> processHlsOnly(Path source, Path hlsDirectory, long deadlineNanos) {
        return java.util.List.of();
    }
}
