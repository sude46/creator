package com.creator.video.domain;

import java.security.MessageDigest;

public record DraftCreation(VideoDraft draft, byte[] fingerprint, boolean accessible) {
    public DraftCreation {
        fingerprint = fingerprint.clone();
    }

    @Override
    public byte[] fingerprint() {
        return fingerprint.clone();
    }

    public boolean matches(VideoMetadata metadata) {
        return MessageDigest.isEqual(fingerprint, metadata.fingerprint());
    }
}
