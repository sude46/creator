package com.creator.video.infrastructure;

import org.apache.ibatis.annotations.*;

@Mapper
public interface FollowMapper {
    @Select("SELECT COUNT(*) > 0 FROM user_follow WHERE follower_id=#{followerId} AND followee_id=#{followeeId}")
    boolean exists(@Param("followerId") long followerId, @Param("followeeId") long followeeId);
    @Insert("INSERT INTO user_follow (follower_id, followee_id) VALUES (#{followerId}, #{followeeId})")
    void add(@Param("followerId") long followerId, @Param("followeeId") long followeeId);
    @Delete("DELETE FROM user_follow WHERE follower_id=#{followerId} AND followee_id=#{followeeId}")
    int remove(@Param("followerId") long followerId, @Param("followeeId") long followeeId);
    @Select("SELECT COUNT(*) FROM user_follow WHERE followee_id=#{userId}")
    long followerCount(long userId);
    @Select("SELECT COUNT(*) FROM user_follow WHERE follower_id=#{userId}")
    long followingCount(long userId);
    @Select("SELECT username FROM user_account WHERE id=#{userId} AND status='ACTIVE'")
    String activeUsername(long userId);
}
