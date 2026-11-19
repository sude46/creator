package com.creator.video.infrastructure;

import com.creator.video.domain.Comment;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CommentMapper {
    @Select("SELECT c.id, c.video_id, c.user_id, owner.username, c.content, c.created_at FROM video_comment c"
            + " JOIN user_account owner ON owner.id=c.user_id WHERE c.video_id=#{videoId} AND c.status='ACTIVE'"
            + " ORDER BY c.id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Comment> page(@Param("videoId") long videoId, @Param("limit") int limit, @Param("offset") int offset);
    @Select("SELECT COUNT(*) FROM video_comment WHERE video_id=#{videoId} AND status='ACTIVE'")
    long count(long videoId);
    @Select("SELECT c.id, c.video_id, c.user_id, owner.username, c.content, c.created_at FROM video_comment c"
            + " JOIN user_account owner ON owner.id=c.user_id WHERE c.id=#{id} AND c.status='ACTIVE'")
    Comment find(long id);
    @Insert("INSERT INTO video_comment (video_id, user_id, content) VALUES (#{videoId}, #{userId}, #{content})")
    void insert(@Param("videoId") long videoId, @Param("userId") long userId, @Param("content") String content);
    @Select("SELECT LAST_INSERT_ID()")
    long lastId();
    @Update("UPDATE video_comment SET status='DELETED', updated_at=CURRENT_TIMESTAMP(6) WHERE id=#{id} AND status='ACTIVE'")
    int delete(long id);
}
