package com.creator.video.infrastructure;

import com.creator.video.domain.ProfileAvatar;
import com.creator.video.domain.UserProfile;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ProfileMapper {
    String COLUMNS = "user_id, display_name, bio, website, avatar IS NOT NULL AS has_avatar, version";

    @Select("SELECT " + COLUMNS + " FROM user_profile WHERE user_id = #{userId}")
    UserProfile find(long userId);

    @Insert("INSERT INTO user_profile (user_id) VALUES (#{userId})")
    void insert(long userId);

    @Update("UPDATE user_profile SET display_name = #{displayName}, bio = #{bio}, website = #{website},"
            + " version = version + 1, updated_at = CURRENT_TIMESTAMP(6)"
            + " WHERE user_id = #{userId} AND version = #{version}")
    int update(@Param("userId") long userId, @Param("version") long version,
            @Param("displayName") String displayName, @Param("bio") String bio, @Param("website") String website);

    @Update("UPDATE user_profile SET avatar = #{data}, avatar_content_type = #{contentType},"
            + " updated_at = CURRENT_TIMESTAMP(6) WHERE user_id = #{userId}")
    int saveAvatar(@Param("userId") long userId, @Param("data") byte[] data, @Param("contentType") String contentType);

    @Update("UPDATE user_profile SET avatar = NULL, avatar_content_type = NULL,"
            + " updated_at = CURRENT_TIMESTAMP(6) WHERE user_id = #{userId}")
    int clearAvatar(long userId);

    @Select("SELECT a.id, a.username, p.display_name, (p.avatar IS NOT NULL) AS has_avatar,"
            + " COALESCE(p.version, 0) AS profile_version"
            + " FROM user_account a LEFT JOIN user_profile p ON p.user_id = a.id"
            + " WHERE a.status = 'ACTIVE' AND (a.username LIKE #{pattern} ESCAPE '!'"
            + " OR p.display_name LIKE #{pattern} ESCAPE '!')"
            + " ORDER BY a.username LIMIT #{limit}")
    java.util.List<com.creator.video.domain.SearchSuggestions.AuthorSuggestion> suggestAuthors(
            @Param("pattern") String pattern, @Param("limit") int limit);

    @Select("SELECT avatar AS data, avatar_content_type AS content_type FROM user_profile"
            + " WHERE user_id = #{userId} AND avatar IS NOT NULL")
    ProfileAvatar avatar(long userId);
}
