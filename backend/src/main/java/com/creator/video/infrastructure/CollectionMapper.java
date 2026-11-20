package com.creator.video.infrastructure;

import com.creator.video.domain.*;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CollectionMapper {
    String COLS = "c.id, c.owner_id, owner.username AS owner_name, c.title, c.description, c.visibility, c.version,"
            + " (SELECT COUNT(*) FROM video_collection_item item WHERE item.collection_id = c.id) AS item_count, c.created_at";
    String FROM = " FROM video_collection c JOIN user_account owner ON owner.id = c.owner_id";

    @Select("SELECT " + COLS + FROM + " WHERE c.owner_id = #{ownerId} ORDER BY c.id DESC LIMIT #{limit} OFFSET #{offset}")
    List<VideoCollection> ownPage(@Param("ownerId") long ownerId, @Param("limit") int limit, @Param("offset") int offset);
    @Select("SELECT COUNT(*) FROM video_collection WHERE owner_id = #{ownerId}")
    long ownCount(long ownerId);
    @Select("SELECT " + COLS + FROM + " WHERE c.owner_id = #{ownerId} AND c.id = #{id}")
    VideoCollection findOwned(@Param("ownerId") long ownerId, @Param("id") long id);
    @Select("SELECT " + COLS + FROM + " WHERE c.owner_id = #{ownerId} AND c.id = #{id} FOR UPDATE")
    VideoCollection lockOwned(@Param("ownerId") long ownerId, @Param("id") long id);
    @Insert("INSERT INTO video_collection (owner_id, title, description, visibility) VALUES (#{ownerId}, #{title}, #{description}, #{visibility})")
    void insert(@Param("ownerId") long ownerId, @Param("title") String title, @Param("description") String description,
            @Param("visibility") String visibility);
    @Select("SELECT LAST_INSERT_ID()")
    long lastId();
    @Update("UPDATE video_collection SET title=#{title}, description=#{description}, visibility=#{visibility},"
            + " version=version+1, updated_at=CURRENT_TIMESTAMP(6) WHERE owner_id=#{ownerId} AND id=#{id} AND version=#{version}")
    int update(@Param("ownerId") long ownerId, @Param("id") long id, @Param("version") long version,
            @Param("title") String title, @Param("description") String description, @Param("visibility") String visibility);
    @Delete("DELETE FROM video_collection WHERE owner_id=#{ownerId} AND id=#{id} AND version=#{version}")
    int delete(@Param("ownerId") long ownerId, @Param("id") long id, @Param("version") long version);
    @Delete("DELETE FROM video_collection_item WHERE collection_id=#{collectionId}")
    void deleteItems(long collectionId);
    @Select("SELECT owner_id FROM video WHERE id=#{videoId} AND deleted_at IS NULL")
    Long videoOwner(long videoId);
    @Select("SELECT COUNT(*) > 0 FROM video_collection_item WHERE collection_id=#{collectionId} AND video_id=#{videoId}")
    boolean itemExists(@Param("collectionId") long collectionId, @Param("videoId") long videoId);
    @Select("SELECT COALESCE(MAX(sort_order), -1) + 1 FROM video_collection_item WHERE collection_id=#{collectionId}")
    int nextOrder(long collectionId);
    @Insert("INSERT INTO video_collection_item (collection_id, video_id, sort_order) VALUES (#{collectionId}, #{videoId}, #{sortOrder})")
    void addItem(@Param("collectionId") long collectionId, @Param("videoId") long videoId, @Param("sortOrder") int sortOrder);
    @Delete("DELETE FROM video_collection_item WHERE collection_id=#{collectionId} AND video_id=#{videoId}")
    int removeItem(@Param("collectionId") long collectionId, @Param("videoId") long videoId);
    @Select("SELECT " + COLS + FROM + " WHERE c.owner_id=#{ownerId} AND c.visibility='PUBLIC' AND owner.status='ACTIVE'"
            + " ORDER BY c.id DESC LIMIT #{limit} OFFSET #{offset}")
    List<VideoCollection> publicByOwner(@Param("ownerId") long ownerId, @Param("limit") int limit, @Param("offset") int offset);
    @Select("SELECT COUNT(*)" + FROM + " WHERE c.owner_id=#{ownerId} AND c.visibility='PUBLIC' AND owner.status='ACTIVE'")
    long publicCountByOwner(long ownerId);
    @Select("SELECT " + COLS + FROM + " WHERE c.id=#{id} AND c.visibility='PUBLIC' AND owner.status='ACTIVE'")
    VideoCollection findPublic(long id);

    @Select("SELECT v.id AS video_id, v.title, v.processing_status, v.review_status, v.publication_status,"
            + " (v.deleted_at IS NOT NULL) AS deleted, item.sort_order"
            + " FROM video_collection_item item JOIN video v ON v.id = item.video_id"
            + " WHERE item.collection_id = #{collectionId} ORDER BY item.sort_order, item.id")
    List<CollectionItem> ownerItems(long collectionId);

    @Update("UPDATE video_collection_item SET sort_order = #{sortOrder} WHERE collection_id = #{collectionId} AND video_id = #{videoId}")
    int updateItemOrder(@Param("collectionId") long collectionId, @Param("videoId") long videoId,
            @Param("sortOrder") int sortOrder);
}
