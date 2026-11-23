package com.creator.video.infrastructure;

import com.creator.video.domain.SubtitleRepository;
import com.creator.video.domain.VideoSubtitle;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SubtitleMapper {
    @Select("SELECT video_id, content, size_bytes, updated_at FROM video_subtitle WHERE video_id = #{videoId}")
    VideoSubtitle find(long videoId);

    @Select("SELECT video_id, size_bytes, updated_at FROM video_subtitle WHERE video_id = #{videoId}")
    SubtitleRepository.Metadata metadata(long videoId);

    @Update("UPDATE video_subtitle SET content = #{content}, size_bytes = #{sizeBytes}, updated_at = CURRENT_TIMESTAMP(6) WHERE video_id = #{videoId}")
    int update(@Param("videoId") long videoId, @Param("content") byte[] content, @Param("sizeBytes") int sizeBytes);

    @Insert("INSERT INTO video_subtitle (video_id, content, size_bytes) VALUES (#{videoId}, #{content}, #{sizeBytes})")
    void insert(@Param("videoId") long videoId, @Param("content") byte[] content, @Param("sizeBytes") int sizeBytes);

    @Delete("DELETE FROM video_subtitle WHERE video_id = #{videoId}")
    void delete(@Param("videoId") long videoId);
}
