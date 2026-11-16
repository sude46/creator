package com.creator.catalog.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CategoryMapper extends BaseMapper<CategoryRow> {
    @Select("""
            SELECT id, name, sort_order, enabled, version FROM video_category
            WHERE (#{enabledOnly} = FALSE OR enabled = TRUE)
            ORDER BY sort_order, id LIMIT #{limit} OFFSET #{offset}
            """)
    List<CategoryRow> findPage(@Param("enabledOnly") boolean enabledOnly, @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM video_category WHERE (#{enabledOnly} = FALSE OR enabled = TRUE)")
    long count(@Param("enabledOnly") boolean enabledOnly);

    @Update("""
            UPDATE video_category SET name = #{name}, name_key = #{nameKey}, sort_order = #{sortOrder},
            enabled = #{enabled}, version = version + 1, updated_at = CURRENT_TIMESTAMP(6)
            WHERE id = #{id} AND version = #{version}
            """)
    int updateVersioned(@Param("id") long id, @Param("version") long version, @Param("name") String name,
            @Param("nameKey") byte[] nameKey, @Param("sortOrder") int sortOrder, @Param("enabled") boolean enabled);

    @Delete("DELETE FROM video_category WHERE id = #{id} AND version = #{version}")
    int deleteVersioned(@Param("id") long id, @Param("version") long version);
}
