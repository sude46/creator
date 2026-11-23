package com.creator.video.domain;

public record HlsPlaylist(String name, int bandwidth, int width, int height, String baseKey, String playlistKey,
        String playlist) {
}
