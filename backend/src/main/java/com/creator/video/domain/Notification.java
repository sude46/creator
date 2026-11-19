package com.creator.video.domain;

import java.time.LocalDateTime;

public record Notification(long id, long userId, String type, String title, String body, String link,
        LocalDateTime readAt, LocalDateTime createdAt) { }
