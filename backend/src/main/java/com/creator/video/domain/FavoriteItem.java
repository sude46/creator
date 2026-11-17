package com.creator.video.domain;

import java.time.LocalDateTime;

public record FavoriteItem(long videoId, LocalDateTime createdAt, PublicVideo video) { }
