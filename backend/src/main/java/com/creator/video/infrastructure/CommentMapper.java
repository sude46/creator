package com.creator.video.infrastructure;

import com.creator.video.domain.Comment;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CommentMapper {
    String COLUMNS = "c.id, c.video_id, c.user_id, owner.username, c.content, c.parent_id,"
            + " (SELECT COUNT(*) FROM video_comment_like liked WHERE liked.comment_id = c.id) AS like_count,"
            + " EXISTS(SELECT 1 FROM video_comment_like liked WHERE liked.comment_id = c.id AND liked.user_id = #{actorId}) AS liked,"
            + " (SELECT COUNT(*) FROM video_comment reply WHERE reply.parent_id = c.id AND reply.status = 'ACTIVE') AS reply_count,"
            + " c.created_at";
    String FROM = " FROM video_comment c JOIN user_account owner ON owner.id = c.user_id";

    @Select("SELECT " + COLUMNS + FROM + " WHERE c.video_id = #{videoId} AND c.status = 'ACTIVE'"
            + " AND c.parent_id IS NULL ORDER BY c.id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Comment> page(@Param("videoId") long videoId, @Param("actorId") long actorId,
            @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM video_comment WHERE video_id = #{videoId} AND status = 'ACTIVE' AND parent_id IS NULL")
    long count(long videoId);

    @Select("SELECT " + COLUMNS + FROM + " WHERE c.parent_id = #{parentId} AND c.status = 'ACTIVE'"
            + " ORDER BY c.id ASC LIMIT #{limit} OFFSET #{offset}")
    List<Comment> replies(@Param("parentId") long parentId, @Param("actorId") long actorId,
            @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM video_comment WHERE parent_id = #{parentId} AND status = 'ACTIVE'")
    long replyCount(long parentId);

    @Select("SELECT " + COLUMNS + FROM + " WHERE c.id = #{id} AND c.status = 'ACTIVE'")
    Comment find(@Param("id") long id, @Param("actorId") long actorId);

    @Insert("INSERT INTO video_comment (video_id, user_id, parent_id, content) VALUES (#{videoId}, #{userId}, #{parentId}, #{content})")
    void insert(@Param("videoId") long videoId, @Param("userId") long userId, @Param("parentId") Long parentId,
            @Param("content") String content);

    @Select("SELECT LAST_INSERT_ID()")
    long lastId();

    @Update("UPDATE video_comment SET status = 'DELETED', updated_at = CURRENT_TIMESTAMP(6)"
            + " WHERE (id = #{id} OR parent_id = #{id}) AND status = 'ACTIVE'")
    int delete(long id);

    @Insert("INSERT INTO video_comment_like (comment_id, user_id) VALUES (#{commentId}, #{userId})")
    void like(@Param("commentId") long commentId, @Param("userId") long userId);

    @Delete("DELETE FROM video_comment_like WHERE comment_id = #{commentId} AND user_id = #{userId}")
    void unlike(@Param("commentId") long commentId, @Param("userId") long userId);

    @Select("SELECT COUNT(*) > 0 FROM video_comment_like WHERE comment_id = #{commentId} AND user_id = #{userId}")
    boolean liked(@Param("commentId") long commentId, @Param("userId") long userId);
}
