package com.creator.video.domain;

public record ProfileAvatar(byte[] data, String contentType) {
    public ProfileAvatar {
        data = data.clone();
    }
    @Override
    public byte[] data() { return data.clone(); }
}
