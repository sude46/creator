package com.creator.video.infrastructure;

import com.creator.video.domain.Notification;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface NotificationMapper {
    @Insert("INSERT INTO notification (user_id,type,title,body,link) VALUES (#{userId},#{type},#{title},#{body},#{link})")
    void add(@Param("userId") long userId, @Param("type") String type, @Param("title") String title,
            @Param("body") String body, @Param("link") String link);
    @Select("SELECT id,user_id,type,title,body,link,read_at,created_at FROM notification WHERE user_id=#{userId} ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Notification> page(@Param("userId") long userId, @Param("limit") int limit, @Param("offset") int offset);
    @Select("SELECT COUNT(*) FROM notification WHERE user_id=#{userId}")
    long count(long userId);
    @Select("SELECT COUNT(*) FROM notification WHERE user_id=#{userId} AND read_at IS NULL")
    long unread(long userId);
    @Update("UPDATE notification SET read_at=CURRENT_TIMESTAMP(6) WHERE id=#{id} AND user_id=#{userId} AND read_at IS NULL")
    int markRead(@Param("userId") long userId, @Param("id") long id);
    @Update("UPDATE notification SET read_at=CURRENT_TIMESTAMP(6) WHERE user_id=#{userId} AND read_at IS NULL")
    void markAll(long userId);
}
