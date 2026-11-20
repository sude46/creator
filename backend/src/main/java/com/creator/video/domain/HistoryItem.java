package com.creator.video.domain;

import java.time.LocalDateTime;

public record HistoryItem(long videoId, LocalDateTime viewedAt, PublicVideo video) { }
