package com.creator.video.infrastructure;

import com.creator.video.domain.DailyViews;
import com.creator.video.domain.TopVideo;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AnalyticsMapper {
    @Update("UPDATE video_view_daily SET view_count = view_count + 1 WHERE video_id = #{videoId} AND view_date = #{date}")
    int incrementDaily(@Param("videoId") long videoId, @Param("date") LocalDate date);

    @Insert("INSERT INTO video_view_daily (video_id, view_date, view_count) VALUES (#{videoId}, #{date}, 1)")
    void insertDaily(@Param("videoId") long videoId, @Param("date") LocalDate date);

    @Select("SELECT COUNT(*) FROM user_follow follow JOIN user_account follower ON follower.id = follow.follower_id"
            + " WHERE follow.followee_id = #{ownerId} AND follower.status = 'ACTIVE'")
    long followerCount(long ownerId);

    @Select("SELECT COUNT(*) FROM video WHERE owner_id = #{ownerId} AND deleted_at IS NULL")
    long videoCount(long ownerId);

    @Select("SELECT COALESCE(SUM(view_count), 0) FROM video WHERE owner_id = #{ownerId} AND deleted_at IS NULL")
    long totalViews(long ownerId);

    @Select("SELECT COALESCE(SUM((SELECT COUNT(*) FROM video_like liked WHERE liked.video_id = v.id)), 0)"
            + " FROM video v WHERE v.owner_id = #{ownerId} AND v.deleted_at IS NULL")
    long totalLikes(long ownerId);

    @Select("SELECT COALESCE(SUM((SELECT COUNT(*) FROM video_favorite favorite WHERE favorite.video_id = v.id)), 0)"
            + " FROM video v WHERE v.owner_id = #{ownerId} AND v.deleted_at IS NULL")
    long totalFavorites(long ownerId);

    @Select("SELECT COALESCE(SUM((SELECT COUNT(*) FROM video_comment comment WHERE comment.video_id = v.id AND comment.status = 'ACTIVE')), 0)"
            + " FROM video v WHERE v.owner_id = #{ownerId} AND v.deleted_at IS NULL")
    long totalComments(long ownerId);

    @Select("SELECT v.id, v.title, v.view_count AS views,"
            + " (SELECT COUNT(*) FROM video_like liked WHERE liked.video_id = v.id) AS likes,"
            + " (SELECT COUNT(*) FROM video_favorite favorite WHERE favorite.video_id = v.id) AS favorites,"
            + " (SELECT COUNT(*) FROM video_comment comment WHERE comment.video_id = v.id AND comment.status = 'ACTIVE') AS comments"
            + " FROM video v WHERE v.owner_id = #{ownerId} AND v.deleted_at IS NULL"
            + " ORDER BY v.view_count DESC, v.id DESC LIMIT 10")
    List<TopVideo> topVideos(long ownerId);

    @Select("SELECT daily.view_date, SUM(daily.view_count) AS views FROM video_view_daily daily"
            + " JOIN video v ON v.id = daily.video_id"
            + " WHERE v.owner_id = #{ownerId} AND daily.view_date BETWEEN #{from} AND #{to}"
            + " GROUP BY daily.view_date ORDER BY daily.view_date")
    List<DailyViews> dailyViews(@Param("ownerId") long ownerId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
