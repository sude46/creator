package com.creator.video.domain;

import com.creator.common.PageRequest;
import java.util.List;
import java.util.Optional;

public interface CommentRepository {
    List<Comment> page(long videoId, PageRequest page);
    long count(long videoId);
    Optional<Comment> find(long id);
    long insert(long videoId, long userId, String content);
    boolean delete(long id);
}
