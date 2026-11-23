package com.creator.video.domain;

import java.util.List;

public record SearchSuggestions(List<VideoSuggestion> videos, List<TagCount> tags, List<AuthorSuggestion> authors) {
    public record VideoSuggestion(long id, String title) { }
    public record AuthorSuggestion(long id, String username, String displayName, boolean hasAvatar, long profileVersion) { }
}
