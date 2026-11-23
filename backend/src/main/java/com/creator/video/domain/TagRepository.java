package com.creator.video.domain;

import java.util.List;
import java.util.Optional;

public interface TagRepository {
    List<String> tags(long videoId);
    List<TagCount> popular(int limit);
    List<TagCount> suggest(String pattern, int limit);
    void replace(long videoId, List<String> tags);
    List<TagAdmin> adminList();
    Optional<TagAdmin> find(long id);
    boolean rename(long id, String name, String key);
    void merge(long sourceId, long targetId);
    boolean delete(long id);
}
