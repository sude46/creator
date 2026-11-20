package com.creator.video.infrastructure;

import com.creator.video.domain.FavoriteFolder;
import com.creator.video.domain.PublicVideo;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface FavoriteFolderMapper {
    String FOLDER_COLUMNS = "f.id, f.owner_id, f.name, f.version,"
            + " (SELECT COUNT(*) FROM favorite_folder_item item JOIN video v ON v.id = item.video_id"
            + " JOIN user_account owner ON owner.id = v.owner_id"
            + " JOIN video_category category ON category.id = v.category_id"
            + " WHERE item.folder_id = f.id AND " + VideoQueryMapper.PUBLIC + ") AS item_count,"
            + " f.created_at";

    @Select("SELECT " + FOLDER_COLUMNS + " FROM favorite_folder f WHERE f.owner_id = #{ownerId} ORDER BY f.id")
    List<FavoriteFolder> list(long ownerId);

    @Select("SELECT COUNT(*) FROM favorite_folder WHERE owner_id = #{ownerId}")
    long count(long ownerId);

    @Select("SELECT " + FOLDER_COLUMNS + " FROM favorite_folder f WHERE f.id = #{id} AND f.owner_id = #{ownerId}")
    FavoriteFolder findOwned(@Param("ownerId") long ownerId, @Param("id") long id);

    @Select("SELECT " + FOLDER_COLUMNS + " FROM favorite_folder f WHERE f.id = #{id} AND f.owner_id = #{ownerId} FOR UPDATE")
    FavoriteFolder lockOwned(@Param("ownerId") long ownerId, @Param("id") long id);

    @Insert("INSERT INTO favorite_folder (owner_id, name) VALUES (#{ownerId}, #{name})")
    void create(@Param("ownerId") long ownerId, @Param("name") String name);

    @Update("UPDATE favorite_folder SET name = #{name}, version = version + 1 WHERE id = #{id} AND owner_id = #{ownerId} AND version = #{version}")
    int rename(@Param("ownerId") long ownerId, @Param("id") long id, @Param("version") long version,
            @Param("name") String name);

    @Delete("DELETE FROM favorite_folder WHERE id = #{id} AND owner_id = #{ownerId}")
    int delete(@Param("ownerId") long ownerId, @Param("id") long id);

    @Insert("INSERT INTO favorite_folder_item (folder_id, video_id) VALUES (#{folderId}, #{videoId})")
    void addItem(@Param("folderId") long folderId, @Param("videoId") long videoId);

    @Delete("DELETE FROM favorite_folder_item WHERE folder_id = #{folderId} AND video_id = #{videoId}")
    void removeItem(@Param("folderId") long folderId, @Param("videoId") long videoId);

    @Delete("DELETE FROM favorite_folder_item WHERE video_id = #{videoId}"
            + " AND folder_id IN (SELECT id FROM favorite_folder WHERE owner_id = #{ownerId})")
    void removeVideoFromAll(@Param("ownerId") long ownerId, @Param("videoId") long videoId);

    @Select("SELECT " + VideoQueryMapper.COLUMNS + VideoQueryMapper.FROM
            + " JOIN favorite_folder_item item ON item.video_id = v.id"
            + " WHERE item.folder_id = #{folderId} AND " + VideoQueryMapper.PUBLIC
            + " ORDER BY item.created_at DESC, item.video_id DESC LIMIT #{limit} OFFSET #{offset}")
    List<PublicVideo> items(@Param("folderId") long folderId, @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM favorite_folder_item item JOIN video v ON v.id = item.video_id"
            + " JOIN user_account owner ON owner.id = v.owner_id"
            + " JOIN video_category category ON category.id = v.category_id"
            + " WHERE item.folder_id = #{folderId} AND " + VideoQueryMapper.PUBLIC)
    long itemCount(long folderId);
}
