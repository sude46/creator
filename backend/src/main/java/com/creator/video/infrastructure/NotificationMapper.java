package com.creator.video.infrastructure;

import com.creator.video.domain.Notification;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface NotificationMapper {
    @Insert("INSERT INTO notification (user_id,type,title,body,link) VALUES (#{userId},#{type},#{title},#{body},#{link})")
    void add(@Param("userId") long userId, @Param("type") String type, @Param("title") String title,
            @Param("body") String body, @Param("link") String link);
    String FILTER = " WHERE user_id=#{userId}"
            + "<if test='unreadOnly'> AND read_at IS NULL</if>"
            + "<if test='type != null'> AND type=#{type}</if>";
    @Select("<script>SELECT id,user_id,type,title,body,link,read_at,created_at FROM notification" + FILTER
            + " ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<Notification> page(@Param("userId") long userId, @Param("unreadOnly") boolean unreadOnly,
            @Param("type") String type, @Param("limit") int limit, @Param("offset") int offset);
    @Select("<script>SELECT COUNT(*) FROM notification" + FILTER + "</script>")
    long count(@Param("userId") long userId, @Param("unreadOnly") boolean unreadOnly, @Param("type") String type);
    @Delete("DELETE FROM notification WHERE id=#{id} AND user_id=#{userId}")
    int delete(@Param("userId") long userId, @Param("id") long id);
    @Delete("DELETE FROM notification WHERE user_id=#{userId} AND read_at IS NOT NULL")
    void clearRead(long userId);
    @Select("SELECT COUNT(*) FROM notification WHERE user_id=#{userId} AND read_at IS NULL")
    long unread(long userId);
    @Update("UPDATE notification SET read_at=CURRENT_TIMESTAMP(6) WHERE id=#{id} AND user_id=#{userId} AND read_at IS NULL")
    int markRead(@Param("userId") long userId, @Param("id") long id);
    @Update("UPDATE notification SET read_at=CURRENT_TIMESTAMP(6) WHERE user_id=#{userId} AND read_at IS NULL")
    void markAll(long userId);
}
