package com.creator.video.application;

import com.creator.video.domain.MediaKind;
import com.creator.video.domain.MediaTarget;
import java.time.Instant;

public interface MediaSigner {
    record SignedMedia(String url, Instant expiresAt) {
        @Override
        public String toString() { return "SignedMedia[url=REDACTED, expiresAt=" + expiresAt + "]"; }
    }
    SignedMedia sign(MediaTarget target, MediaKind kind);

    default SignedMedia sign(MediaTarget target, MediaKind kind, boolean attachment) {
        return sign(target, kind);
    }

    default SignedMedia signHlsSegment(MediaTarget target, int seconds) {
        return sign(target, MediaKind.SOURCE);
    }
}
