package com.creator.video.infrastructure;

import com.creator.video.domain.TagAdmin;
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
            + " AND tag.name LIKE #{pattern} ESCAPE '!'"
            + " GROUP BY tag.id, tag.name ORDER BY uses DESC, tag.name LIMIT #{limit}")
    List<TagCount> suggest(@Param("pattern") String pattern, @Param("limit") int limit);

    @Select("SELECT tag.name AS name, COUNT(*) AS uses FROM video_tag tag"
            + " JOIN video_tag_item item ON item.tag_id = tag.id" + PUBLIC_USES
            + " GROUP BY tag.id, tag.name ORDER BY uses DESC, tag.name LIMIT #{limit}")
    List<TagCount> popular(int limit);

    @Select("SELECT t.id, t.name, COUNT(item.video_id) AS uses FROM video_tag t"
            + " LEFT JOIN video_tag_item item ON item.tag_id = t.id"
            + " GROUP BY t.id, t.name ORDER BY uses DESC, t.name")
    List<TagAdmin> adminList();

    @Select("SELECT t.id, t.name, COUNT(item.video_id) AS uses FROM video_tag t"
            + " LEFT JOIN video_tag_item item ON item.tag_id = t.id"
            + " WHERE t.id = #{id} GROUP BY t.id, t.name")
    TagAdmin findAdmin(long id);

    @Update("UPDATE video_tag SET name = #{name}, name_key = #{key} WHERE id = #{id}")
    int rename(@Param("id") long id, @Param("name") String name, @Param("key") String key);

    @Insert("INSERT INTO video_tag_item (video_id, tag_id)"
            + " SELECT item.video_id, #{targetId} FROM video_tag_item item"
            + " WHERE item.tag_id = #{sourceId}"
            + " AND NOT EXISTS (SELECT 1 FROM video_tag_item existing"
            + " WHERE existing.video_id = item.video_id AND existing.tag_id = #{targetId})")
    int moveItems(@Param("sourceId") long sourceId, @Param("targetId") long targetId);

    @Delete("DELETE FROM video_tag_item WHERE tag_id = #{tagId}")
    void deleteTagItems(long tagId);

    @Delete("DELETE FROM video_tag WHERE id = #{id}")
    int deleteTag(long id);
}
