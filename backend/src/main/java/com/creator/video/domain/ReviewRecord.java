package com.creator.video.domain;

import java.time.LocalDateTime;

public record ReviewRecord(long id, long videoId, long reviewRound, long contentVersion, long processingGeneration,
        long reviewerId, ReviewDecision.Outcome outcome, String reason, String title, String description,
        long categoryId, LocalDateTime createdAt) { }
