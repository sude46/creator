package com.creator.video.domain;

public record AuthorCard(long id, String username, String displayName, String bio, boolean hasAvatar,
        long profileVersion, long followerCount, boolean following) { }
