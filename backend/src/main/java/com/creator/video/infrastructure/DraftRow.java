package com.creator.video.infrastructure;

import com.creator.video.domain.DraftCreation;
import com.creator.video.domain.VideoDraft;
import com.creator.video.domain.VideoMetadata;

public record DraftRow(Long id, Long ownerId, Long categoryId, String title, String description,
        byte[] creationFingerprint, Long version, Boolean accessible) {
    public VideoDraft toDraft() {
        return new VideoDraft(id, ownerId, new VideoMetadata(title, description, categoryId), version);
    }

    public DraftCreation toCreation() {
        return new DraftCreation(toDraft(), creationFingerprint, accessible);
    }
}
