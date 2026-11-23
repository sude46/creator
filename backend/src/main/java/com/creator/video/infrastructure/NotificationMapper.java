package com.creator.video.infrastructure;

import com.creator.video.domain.Notification;
import com.creator.video.domain.NotificationAggregate;
import java.time.LocalDateTime;
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
            + " ORDER BY updated_at DESC, id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<Notification> page(@Param("userId") long userId, @Param("unreadOnly") boolean unreadOnly,
            @Param("type") String type, @Param("limit") int limit, @Param("offset") int offset);
    @Select("<script>SELECT COUNT(*) FROM notification" + FILTER + "</script>")
    long count(@Param("userId") long userId, @Param("unreadOnly") boolean unreadOnly, @Param("type") String type);
    @Delete("DELETE FROM notification WHERE id=#{id} AND user_id=#{userId}")
    int delete(@Param("userId") long userId, @Param("id") long id);
    @Delete("DELETE FROM notification WHERE user_id=#{userId} AND read_at IS NOT NULL")
    void clearRead(long userId);

    @Select("SELECT id, aggregate_count AS count FROM notification"
            + " WHERE user_id=#{userId} AND type=#{type} AND aggregate_key=#{key} AND read_at IS NULL"
            + " ORDER BY updated_at DESC LIMIT 1")
    NotificationAggregate findAggregate(@Param("userId") long userId, @Param("type") String type,
            @Param("key") String key);

    @Insert("INSERT INTO notification (user_id,type,title,body,link,aggregate_key,aggregate_count)"
            + " VALUES (#{userId},#{type},#{title},#{body},#{link},#{key},1)")
    void addAggregated(@Param("userId") long userId, @Param("type") String type, @Param("key") String key,
            @Param("title") String title, @Param("body") String body, @Param("link") String link);

    @Update("UPDATE notification SET title=#{title}, body=#{body}, aggregate_count=#{count}, updated_at=#{now}"
            + " WHERE id=#{id} AND read_at IS NULL")
    int updateAggregated(@Param("id") long id, @Param("title") String title, @Param("body") String body,
            @Param("count") int count, @Param("now") LocalDateTime now);
    @Select("SELECT COUNT(*) FROM notification WHERE user_id=#{userId} AND read_at IS NULL")
    long unread(long userId);
    @Update("UPDATE notification SET read_at=CURRENT_TIMESTAMP(6) WHERE id=#{id} AND user_id=#{userId} AND read_at IS NULL")
    int markRead(@Param("userId") long userId, @Param("id") long id);
    @Update("UPDATE notification SET read_at=CURRENT_TIMESTAMP(6) WHERE user_id=#{userId} AND read_at IS NULL")
    void markAll(long userId);
}
