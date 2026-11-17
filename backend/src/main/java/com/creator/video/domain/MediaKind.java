package com.creator.video.domain;

public enum MediaKind {
    SOURCE("video/mp4"), COVER("image/jpeg");
    private final String contentType;
    MediaKind(String contentType) { this.contentType = contentType; }
    public String contentType() { return contentType; }
}
