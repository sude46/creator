package com.creator.video.domain;

import java.util.List;

public interface TagRepository {
    List<String> tags(long videoId);
    List<TagCount> popular(int limit);
    void replace(long videoId, List<String> tags);
}
