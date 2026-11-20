package com.creator.video.domain;

import java.time.LocalDateTime;

public record FavoriteFolder(long id, long ownerId, String name, long version, long itemCount, LocalDateTime createdAt) { }
