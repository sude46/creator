package com.creator.video.domain;

import java.time.LocalDateTime;

public record VideoCollection(long id, long ownerId, String ownerName, String title, String description,
        String visibility, long version, long itemCount, LocalDateTime createdAt) { }
