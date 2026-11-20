package com.creator.video.web;

import com.creator.video.domain.VideoMetadata;
import jakarta.validation.constraints.*;

public record UpdateVideoRequest(@NotBlank @Size(max = 120) String title,
        @NotNull @Size(max = 2000) String description, @NotNull @Positive Long categoryId,
        @NotNull @PositiveOrZero Long version, java.util.List<String> tags) {
    public UpdateVideoRequest {
        tags = tags == null ? java.util.List.of() : java.util.List.copyOf(tags);
    }
    public VideoMetadata metadata() {
        return new VideoMetadata(title, description, categoryId, tags);
    }
}
