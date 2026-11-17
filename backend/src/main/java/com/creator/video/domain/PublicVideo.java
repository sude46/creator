package com.creator.video.domain;

import java.time.LocalDateTime;

public record PublicVideo(long id, long ownerId, String authorName, long categoryId, String categoryName,
        String title, String description, long durationMillis, int mediaWidth, int mediaHeight, LocalDateTime publishedAt) { }
