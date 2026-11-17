package com.creator.video.infrastructure;

import com.creator.video.domain.VideoMetadata;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface DraftMapper {
    String DRAFT_CONDITION = "processing_status = 'PENDING' AND review_status = 'NOT_SUBMITTED'"
            + " AND publication_status = 'UNPUBLISHED' AND moderation_blocked = FALSE AND deleted_at IS NULL AND source_asset_id IS NULL";
    String COLUMNS = "id, owner_id, category_id, title, description, creation_fingerprint, version, ("
            + DRAFT_CONDITION + ") AS `accessible`";

    @Select("SELECT " + COLUMNS + " FROM video WHERE owner_id = #{ownerId} AND client_request_id = #{key}")
    DraftRow findCreation(@Param("ownerId") long ownerId, @Param("key") String key);

    @Select("SELECT " + COLUMNS + " FROM video WHERE owner_id = #{ownerId} AND client_request_id = #{key} FOR UPDATE")
    DraftRow lockCreation(@Param("ownerId") long ownerId, @Param("key") String key);

    @Select("SELECT " + COLUMNS + " FROM video WHERE owner_id = #{ownerId} AND id = #{id} AND " + DRAFT_CONDITION)
    DraftRow findOwned(@Param("ownerId") long ownerId, @Param("id") long id);

    @Select("SELECT " + COLUMNS + " FROM video WHERE owner_id = #{ownerId} AND " + DRAFT_CONDITION + " ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    List<DraftRow> findPage(@Param("ownerId") long ownerId, @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM video WHERE owner_id = #{ownerId} AND " + DRAFT_CONDITION)
    long count(long ownerId);

    @Insert("""
            INSERT INTO video (owner_id, category_id, title, description, client_request_id, creation_fingerprint)
            VALUES (#{ownerId}, #{metadata.categoryId}, #{metadata.title}, #{metadata.description}, #{key}, #{fingerprint})
            """)
    void insert(@Param("ownerId") long ownerId, @Param("key") String key,
            @Param("metadata") VideoMetadata metadata, @Param("fingerprint") byte[] fingerprint);

    @Update("""
            UPDATE video SET category_id = #{metadata.categoryId}, title = #{metadata.title},
            description = #{metadata.description}, version = version + 1, updated_at = CURRENT_TIMESTAMP(6)
            WHERE id = #{id} AND owner_id = #{ownerId} AND version = #{version} AND
            """ + DRAFT_CONDITION)
    int update(@Param("ownerId") long ownerId, @Param("id") long id, @Param("version") long version,
            @Param("metadata") VideoMetadata metadata);
}
