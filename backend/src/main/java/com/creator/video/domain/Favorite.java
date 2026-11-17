package com.creator.video.domain;

import java.time.LocalDateTime;

public record Favorite(long id, long videoId, LocalDateTime createdAt) { }
