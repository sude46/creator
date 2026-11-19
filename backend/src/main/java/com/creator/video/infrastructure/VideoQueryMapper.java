package com.creator.video.infrastructure;

import com.creator.video.domain.*;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface VideoQueryMapper {
    String FROM = " FROM video v JOIN user_account owner ON owner.id = v.owner_id"
            + " JOIN video_category category ON category.id = v.category_id";
    String READY = "v.processing_status = 'READY' AND v.deleted_at IS NULL AND v.moderation_blocked = FALSE"
            + " AND owner.status = 'ACTIVE' AND v.duration_millis IS NOT NULL AND v.media_width IS NOT NULL AND v.media_height IS NOT NULL"
            + " AND EXISTS (SELECT 1 FROM media_asset source WHERE source.id = v.source_asset_id AND source.video_id = v.id AND source.kind = 'SOURCE')"
            + " AND EXISTS (SELECT 1 FROM media_asset cover WHERE cover.video_id = v.id AND cover.kind = 'COVER')"
            + " AND EXISTS (SELECT 1 FROM processing_task task WHERE task.video_id = v.id AND task.generation = v.processing_generation AND task.state = 'SUCCEEDED')";
    String PUBLIC = READY + " AND v.review_status = 'APPROVED' AND v.publication_status = 'PUBLISHED' AND v.published_at IS NOT NULL";
    String COLUMNS = "v.id, v.owner_id, owner.username AS author_name, v.category_id, category.name AS category_name,"
            + " v.title, v.description, v.duration_millis, v.media_width, v.media_height, v.published_at,"
            + " (SELECT cover.object_key FROM media_asset cover WHERE cover.video_id = v.id AND cover.kind = 'COVER'"
            + " ORDER BY cover.id DESC LIMIT 1) AS cover_key, v.view_count,"
            + " (SELECT COUNT(*) FROM video_favorite favorite WHERE favorite.video_id = v.id) AS favorite_count,"
            + " (SELECT COUNT(*) FROM video_like liked WHERE liked.video_id = v.id) AS like_count";
    String FILTER = "<if test='title != null'> AND v.title LIKE #{pattern} ESCAPE '!'</if>"
            + "<if test='categoryId != null'> AND v.category_id = #{categoryId}</if>";

    @Select("<script>SELECT " + COLUMNS + FROM + " WHERE " + PUBLIC + FILTER
            + " ORDER BY v.published_at DESC, v.id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<PublicVideo> findPage(@Param("title") String title, @Param("pattern") String pattern,
            @Param("categoryId") Long categoryId, @Param("limit") int limit, @Param("offset") int offset);

    @Select("<script>SELECT COUNT(*)" + FROM + " WHERE " + PUBLIC + FILTER + "</script>")
    long count(@Param("title") String title, @Param("pattern") String pattern, @Param("categoryId") Long categoryId);

    @Select("SELECT " + COLUMNS + FROM + " WHERE " + PUBLIC + " AND v.id = #{videoId}")
    PublicVideo findPublic(long videoId);

    @Update("UPDATE video SET view_count = view_count + 1 WHERE id = #{videoId}"
            + " AND processing_status = 'READY' AND review_status = 'APPROVED' AND publication_status = 'PUBLISHED'"
            + " AND deleted_at IS NULL AND moderation_blocked = FALSE")
    int incrementView(@Param("videoId") long videoId);

    @Select("<script>SELECT " + COLUMNS + FROM + " WHERE " + PUBLIC + " AND v.id = #{videoId}" + FILTER + "</script>")
    PublicVideo findPublicInSearch(@Param("videoId") long videoId, @Param("title") String title,
            @Param("pattern") String pattern, @Param("categoryId") Long categoryId);

    @Select("<script>SELECT * FROM (SELECT " + COLUMNS + FROM + " WHERE " + PUBLIC + FILTER
            + " AND (v.published_at &gt; #{publishedAt} OR (v.published_at = #{publishedAt} AND v.id &gt; #{videoId}))"
            + " ORDER BY v.published_at ASC, v.id ASC LIMIT #{limit}) newer"
            + " ORDER BY newer.published_at DESC, newer.id DESC</script>")
    List<PublicVideo> findNewer(@Param("title") String title, @Param("pattern") String pattern,
            @Param("categoryId") Long categoryId, @Param("publishedAt") java.time.LocalDateTime publishedAt,
            @Param("videoId") long videoId, @Param("limit") int limit);

    @Select("<script>SELECT " + COLUMNS + FROM + " WHERE " + PUBLIC + FILTER
            + " AND (v.published_at &lt; #{publishedAt} OR (v.published_at = #{publishedAt} AND v.id &lt; #{videoId}))"
            + " ORDER BY v.published_at DESC, v.id DESC LIMIT #{limit}</script>")
    List<PublicVideo> findOlder(@Param("title") String title, @Param("pattern") String pattern,
            @Param("categoryId") Long categoryId, @Param("publishedAt") java.time.LocalDateTime publishedAt,
            @Param("videoId") long videoId, @Param("limit") int limit);

    @Select("<script>SELECT " + COLUMNS + FROM + " WHERE " + PUBLIC
            + " AND v.id IN <foreach collection='videoIds' item='videoId' open='(' separator=',' close=')'>#{videoId}</foreach></script>")
    List<PublicVideo> findPublicByIds(@Param("videoIds") List<Long> videoIds);

    @Select("SELECT " + COLUMNS + FROM + " WHERE " + PUBLIC
            + " AND v.owner_id = #{authorId} ORDER BY v.published_at DESC, v.id DESC LIMIT #{limit} OFFSET #{offset}")
    List<PublicVideo> findPageByAuthor(@Param("authorId") long authorId, @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*)" + FROM + " WHERE " + PUBLIC + " AND v.owner_id = #{authorId}")
    long countByAuthor(@Param("authorId") long authorId);

    @Select("SELECT " + COLUMNS + FROM + " JOIN video_collection_item item ON item.video_id = v.id"
            + " WHERE item.collection_id = #{collectionId} AND " + PUBLIC
            + " ORDER BY item.sort_order, item.id LIMIT #{limit} OFFSET #{offset}")
    List<PublicVideo> findPublicByCollection(@Param("collectionId") long collectionId,
            @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*)" + FROM + " JOIN video_collection_item item ON item.video_id = v.id"
            + " WHERE item.collection_id = #{collectionId} AND " + PUBLIC)
    long countPublicByCollection(long collectionId);

    @Select("<script>SELECT asset.object_key, asset.size_bytes" + FROM
            + " JOIN media_asset asset ON asset.video_id = v.id AND asset.kind = #{kind}"
            + " WHERE v.id = #{videoId} AND " + READY
            + "<choose><when test=\"purpose.name() == 'PUBLIC_PLAY'\"> AND " + PUBLIC + "</when>"
            + "<when test=\"purpose.name() == 'OWNER_PREVIEW' || purpose.name() == 'OWNER_DOWNLOAD'\"> AND v.owner_id = #{actorId}</when>"
            + "<when test=\"purpose.name() == 'REVIEW_PREVIEW' || purpose.name() == 'REVIEW_DOWNLOAD'\"> AND v.owner_id != #{actorId} AND v.review_status = 'PENDING'"
            + " AND v.version = #{version} AND v.review_round = #{round}</when><otherwise> AND 1 = 0</otherwise></choose>"
            + "<if test=\"kind.name() == 'SOURCE'\"> AND asset.id = v.source_asset_id</if></script>")
    MediaTarget media(@Param("videoId") long videoId, @Param("actorId") long actorId, @Param("purpose") MediaPurpose purpose,
            @Param("kind") MediaKind kind, @Param("version") Long version, @Param("round") Long round);
}
