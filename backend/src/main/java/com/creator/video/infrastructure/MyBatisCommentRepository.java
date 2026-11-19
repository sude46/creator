package com.creator.video.infrastructure;

import com.creator.common.PageRequest;
import com.creator.video.domain.Comment;
import com.creator.video.domain.CommentRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisCommentRepository implements CommentRepository {
    private final CommentMapper mapper;
    public MyBatisCommentRepository(CommentMapper mapper) { this.mapper = mapper; }
    public List<Comment> page(long videoId, PageRequest page) { return mapper.page(videoId, page.size(), page.offset()); }
    public long count(long videoId) { return mapper.count(videoId); }
    public Optional<Comment> find(long id) { return Optional.ofNullable(mapper.find(id)); }
    public long insert(long videoId, long userId, String content) { mapper.insert(videoId, userId, content); return mapper.lastId(); }
    public boolean delete(long id) { return mapper.delete(id) == 1; }
}
