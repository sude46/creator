package com.creator.video.infrastructure;

import com.creator.common.PageRequest;
import com.creator.video.domain.Comment;
import com.creator.video.domain.CommentRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisCommentRepository implements CommentRepository {
    private final CommentMapper mapper;
    public MyBatisCommentRepository(CommentMapper mapper) { this.mapper = mapper; }
    public List<Comment> page(long videoId, long actorId, String sort, PageRequest page) {
        return mapper.page(videoId, actorId, sort, page.size(), page.offset());
    }
    public long count(long videoId) { return mapper.count(videoId); }
    public List<Comment> replies(long parentId, long actorId, PageRequest page) {
        return mapper.replies(parentId, actorId, page.size(), page.offset());
    }
    public long replyCount(long parentId) { return mapper.replyCount(parentId); }
    public Optional<Comment> find(long id) { return find(id, 0); }
    public Optional<Comment> find(long id, long actorId) { return Optional.ofNullable(mapper.find(id, actorId)); }
    public long insert(long videoId, long userId, Long parentId, String content) {
        mapper.insert(videoId, userId, parentId, content);
        return mapper.lastId();
    }
    public boolean delete(long id) { return mapper.delete(id) > 0; }
    public void like(long commentId, long userId) {
        try { mapper.like(commentId, userId); } catch (DuplicateKeyException ignored) { }
    }
    public void unlike(long commentId, long userId) { mapper.unlike(commentId, userId); }
    public boolean liked(long commentId, long userId) { return mapper.liked(commentId, userId); }
}
