package com.creator.video.infrastructure;

import org.apache.ibatis.annotations.*;

@Mapper
public interface LikeMapper {
    @Select("SELECT COUNT(*) > 0 FROM video_like WHERE user_id=#{userId} AND video_id=#{videoId}")
    boolean exists(@Param("userId") long userId, @Param("videoId") long videoId);
    @Insert("INSERT INTO video_like (user_id, video_id) VALUES (#{userId}, #{videoId})")
    void add(@Param("userId") long userId, @Param("videoId") long videoId);
    @Delete("DELETE FROM video_like WHERE user_id=#{userId} AND video_id=#{videoId}")
    int remove(@Param("userId") long userId, @Param("videoId") long videoId);
}
