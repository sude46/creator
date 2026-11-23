package com.creator.video.infrastructure;

import com.creator.video.domain.SubtitleRepository;
import com.creator.video.domain.VideoSubtitle;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SubtitleMapper {
    @Select("SELECT video_id, language, label, content, size_bytes, updated_at FROM video_subtitle WHERE video_id = #{videoId} AND language = #{language}")
    VideoSubtitle find(@Param("videoId") long videoId, @Param("language") String language);

    @Select("SELECT video_id, language, label, size_bytes, updated_at FROM video_subtitle WHERE video_id = #{videoId} ORDER BY language")
    List<SubtitleRepository.Metadata> list(long videoId);

    @Select("SELECT video_id, language, label, size_bytes, updated_at FROM video_subtitle WHERE video_id = #{videoId} AND language = #{language}")
    SubtitleRepository.Metadata metadata(@Param("videoId") long videoId, @Param("language") String language);

    @Update("UPDATE video_subtitle SET label = #{label}, content = #{content}, size_bytes = #{sizeBytes}, updated_at = CURRENT_TIMESTAMP(6) WHERE video_id = #{videoId} AND language = #{language}")
    int update(@Param("videoId") long videoId, @Param("language") String language, @Param("label") String label,
            @Param("content") byte[] content, @Param("sizeBytes") int sizeBytes);

    @Insert("INSERT INTO video_subtitle (video_id, language, label, content, size_bytes) VALUES (#{videoId}, #{language}, #{label}, #{content}, #{sizeBytes})")
    void insert(@Param("videoId") long videoId, @Param("language") String language, @Param("label") String label,
            @Param("content") byte[] content, @Param("sizeBytes") int sizeBytes);

    @Delete("DELETE FROM video_subtitle WHERE video_id = #{videoId} AND language = #{language}")
    void delete(@Param("videoId") long videoId, @Param("language") String language);
}
