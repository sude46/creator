package com.creator.video.domain;

public record UserProfile(long userId, String displayName, String bio, String website, boolean hasAvatar, long version) { }
