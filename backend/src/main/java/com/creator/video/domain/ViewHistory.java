package com.creator.video.domain;

import java.time.LocalDateTime;

public record ViewHistory(long id, long videoId, LocalDateTime viewedAt) { }
