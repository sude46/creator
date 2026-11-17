package com.creator.video.infrastructure;

import com.creator.video.domain.Favorite;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface FavoriteMapper {
    @Insert("INSERT INTO video_favorite (user_id, video_id) VALUES (#{userId}, #{videoId})")
    void add(@Param("userId") long userId, @Param("videoId") long videoId);

    @Delete("DELETE FROM video_favorite WHERE user_id = #{userId} AND video_id = #{videoId}")
    void remove(@Param("userId") long userId, @Param("videoId") long videoId);

    @Select("SELECT COUNT(*) > 0 FROM video_favorite WHERE user_id = #{userId} AND video_id = #{videoId}")
    boolean contains(@Param("userId") long userId, @Param("videoId") long videoId);

    @Select("SELECT id, video_id, created_at FROM video_favorite WHERE user_id = #{userId} ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Favorite> findPage(@Param("userId") long userId, @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM video_favorite WHERE user_id = #{userId}")
    long count(long userId);
}
