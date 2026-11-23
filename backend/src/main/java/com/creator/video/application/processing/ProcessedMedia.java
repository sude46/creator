package com.creator.video.application.processing;

import com.creator.video.domain.MediaMetadata;
import java.util.List;

public record ProcessedMedia(MediaMetadata metadata, List<HlsVariant> variants) {
    public ProcessedMedia {
        variants = List.copyOf(variants);
    }

    public record HlsVariant(String name, int bandwidth, int width, int height, String playlist) { }
}
