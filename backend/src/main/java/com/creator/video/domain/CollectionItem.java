package com.creator.video.domain;

public record CollectionItem(long videoId, String title, String processingStatus, String reviewStatus,
        String publicationStatus, boolean deleted, int sortOrder) { }
