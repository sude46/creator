package com.creator.video.domain;

import java.time.LocalDateTime;

public record Comment(long id, long videoId, long userId, String username, String content, LocalDateTime createdAt) { }
