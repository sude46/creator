package com.creator.video.infrastructure;

import com.creator.video.domain.AuthorCard;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface FollowMapper {
    @Select("SELECT COUNT(*) > 0 FROM user_follow WHERE follower_id=#{followerId} AND followee_id=#{followeeId}")
    boolean exists(@Param("followerId") long followerId, @Param("followeeId") long followeeId);
    @Insert("INSERT INTO user_follow (follower_id, followee_id) VALUES (#{followerId}, #{followeeId})")
    void add(@Param("followerId") long followerId, @Param("followeeId") long followeeId);
    @Delete("DELETE FROM user_follow WHERE follower_id=#{followerId} AND followee_id=#{followeeId}")
    int remove(@Param("followerId") long followerId, @Param("followeeId") long followeeId);
    String CARD = "a.id, a.username, p.display_name, p.bio, p.avatar IS NOT NULL AS has_avatar,"
            + " COALESCE(p.version, 0) AS profile_version,"
            + " (SELECT COUNT(*) FROM user_follow counted JOIN user_account counted_user ON counted_user.id = counted.follower_id"
            + " WHERE counted.followee_id = a.id AND counted_user.status = 'ACTIVE') AS follower_count,"
            + " EXISTS(SELECT 1 FROM user_follow mine WHERE mine.follower_id = #{viewerId} AND mine.followee_id = a.id) AS following";
    @Select("SELECT COUNT(*) FROM user_follow follow JOIN user_account a ON a.id=follow.follower_id"
            + " WHERE follow.followee_id=#{userId} AND a.status='ACTIVE'")
    long followerCount(long userId);
    @Select("SELECT COUNT(*) FROM user_follow follow JOIN user_account a ON a.id=follow.followee_id"
            + " WHERE follow.follower_id=#{userId} AND a.status='ACTIVE'")
    long followingCount(long userId);
    @Select("SELECT " + CARD + " FROM user_follow follow JOIN user_account a ON a.id=follow.follower_id"
            + " LEFT JOIN user_profile p ON p.user_id=a.id"
            + " WHERE follow.followee_id=#{userId} AND a.status='ACTIVE'"
            + " ORDER BY follow.id DESC LIMIT #{limit} OFFSET #{offset}")
    List<AuthorCard> followers(@Param("userId") long userId, @Param("viewerId") long viewerId,
            @Param("limit") int limit, @Param("offset") int offset);
    @Select("SELECT " + CARD + " FROM user_follow follow JOIN user_account a ON a.id=follow.followee_id"
            + " LEFT JOIN user_profile p ON p.user_id=a.id"
            + " WHERE follow.follower_id=#{userId} AND a.status='ACTIVE'"
            + " ORDER BY follow.id DESC LIMIT #{limit} OFFSET #{offset}")
    List<AuthorCard> following(@Param("userId") long userId, @Param("viewerId") long viewerId,
            @Param("limit") int limit, @Param("offset") int offset);
    @Select("SELECT username FROM user_account WHERE id=#{userId} AND status='ACTIVE'")
    String activeUsername(long userId);
}
