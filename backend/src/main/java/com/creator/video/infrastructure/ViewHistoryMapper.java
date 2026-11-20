package com.creator.video.infrastructure;

import com.creator.video.domain.ViewHistory;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ViewHistoryMapper {
    @Update("UPDATE view_history SET viewed_at = CURRENT_TIMESTAMP(6) WHERE user_id = #{userId} AND video_id = #{videoId}")
    int touch(@Param("userId") long userId, @Param("videoId") long videoId);

    @Insert("INSERT INTO view_history (user_id, video_id) VALUES (#{userId}, #{videoId})")
    void insert(@Param("userId") long userId, @Param("videoId") long videoId);

    @Select("SELECT id, video_id, viewed_at FROM view_history WHERE user_id = #{userId}"
            + " ORDER BY viewed_at DESC, id DESC LIMIT #{limit} OFFSET #{offset}")
    List<ViewHistory> findPage(@Param("userId") long userId, @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM view_history WHERE user_id = #{userId}")
    long count(long userId);

    @Select("SELECT id FROM view_history WHERE user_id = #{userId} ORDER BY viewed_at DESC, id DESC LIMIT 500 OFFSET 200")
    List<Long> staleIds(long userId);

    @Delete("<script>DELETE FROM view_history WHERE user_id = #{userId} AND id IN"
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    void deleteIds(@Param("userId") long userId, @Param("ids") List<Long> ids);

    @Delete("DELETE FROM view_history WHERE user_id = #{userId} AND video_id = #{videoId}")
    void remove(@Param("userId") long userId, @Param("videoId") long videoId);

    @Delete("DELETE FROM view_history WHERE user_id = #{userId}")
    void clear(long userId);
}
