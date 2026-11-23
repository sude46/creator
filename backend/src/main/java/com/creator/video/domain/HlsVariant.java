package com.creator.video.domain;

import java.util.List;

public record HlsVariant(String name, int bandwidth, int width, int height, String baseKey, String playlistKey,
        String playlist, List<String> segmentKeys) {
    public HlsVariant {
        segmentKeys = List.copyOf(segmentKeys);
    }
}
