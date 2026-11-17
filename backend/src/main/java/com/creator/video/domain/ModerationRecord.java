package com.creator.video.domain;

import java.time.LocalDateTime;

public record ModerationRecord(long id, long actorId, String action, String reason, LocalDateTime createdAt) { }
