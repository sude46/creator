package com.creator.video.domain;

import com.creator.common.PageRequest;
import java.util.List;
import java.util.Optional;

public interface CommentRepository {
    List<Comment> page(long videoId, long actorId, String sort, PageRequest page);
    long count(long videoId);
    List<Comment> replies(long parentId, long actorId, PageRequest page);
    long replyCount(long parentId);
    Optional<Comment> find(long id);
    Optional<Comment> find(long id, long actorId);
    Optional<CommentAdmin> findAny(long id);
    boolean restore(long id);
    long insert(long videoId, long userId, Long parentId, String content);
    boolean delete(long id);
    void like(long commentId, long userId);
    void unlike(long commentId, long userId);
    boolean liked(long commentId, long userId);
}
