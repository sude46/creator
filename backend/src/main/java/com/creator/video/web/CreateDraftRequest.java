package com.creator.video.web;

import com.creator.video.domain.VideoMetadata;
import jakarta.validation.constraints.*;

public record CreateDraftRequest(@NotBlank @Size(max = 36) String requestKey,
        @NotBlank @Size(max = 120) String title, @NotNull @Size(max = 2000) String description,
        @NotNull @Positive Long categoryId) {
    public VideoMetadata metadata() {
        return new VideoMetadata(title, description, categoryId);
    }
}
