package com.creator.video.infrastructure;

import com.creator.video.domain.TagCount;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface TagMapper {
    String PUBLIC_USES = " JOIN video v ON v.id = item.video_id"
            + " JOIN user_account owner ON owner.id = v.owner_id"
            + " JOIN video_category category ON category.id = v.category_id WHERE " + VideoQueryMapper.PUBLIC;

    @Select("SELECT tag.name FROM video_tag tag JOIN video_tag_item item ON item.tag_id = tag.id"
            + " WHERE item.video_id = #{videoId} ORDER BY tag.name")
    List<String> names(long videoId);

    @Select("SELECT id FROM video_tag WHERE name_key = #{key}")
    Long idByKey(@Param("key") String key);

    @Insert("INSERT INTO video_tag (name, name_key) VALUES (#{name}, #{key})")
    void insertTag(@Param("name") String name, @Param("key") String key);

    @Insert("INSERT INTO video_tag_item (video_id, tag_id) VALUES (#{videoId}, #{tagId})")
    void insertItem(@Param("videoId") long videoId, @Param("tagId") long tagId);

    @Delete("DELETE FROM video_tag_item WHERE video_id = #{videoId}")
    void deleteItems(long videoId);

    @Select("SELECT tag.name AS name, COUNT(*) AS uses FROM video_tag tag"
            + " JOIN video_tag_item item ON item.tag_id = tag.id" + PUBLIC_USES
            + " GROUP BY tag.id, tag.name ORDER BY uses DESC, tag.name LIMIT #{limit}")
    List<TagCount> popular(int limit);
}
