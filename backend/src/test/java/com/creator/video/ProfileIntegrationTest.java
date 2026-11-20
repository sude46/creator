package com.creator.video;

import com.creator.catalog.application.CategoryService;
import com.creator.common.*;
import com.creator.identity.application.*;
import com.creator.identity.domain.*;
import com.creator.video.application.*;
import com.creator.video.domain.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ProfileIntegrationTest {
    @Autowired JdbcTemplate database;
    @Autowired ProfileService profiles;
    @Autowired ProfileRepository profileRepository;
    @Autowired FollowService follows;
    @Autowired DraftService drafts;
    @Autowired ReviewService reviews;
    @Autowired PublicVideoService publicVideos;
    @Autowired CategoryService categories;
    @Autowired RegisterUser registration;
    @Autowired BootstrapAdmin bootstrap;
    @Autowired AccountRepository accounts;
    Actor author;
    Actor admin;
    long categoryId;

    @BeforeEach
    void reset() {
        database.update("DELETE FROM operation_log");
        database.update("DELETE FROM review_record");
        database.update("DELETE FROM processing_task");
        database.update("DELETE FROM upload_attempt");
        database.update("UPDATE video SET source_asset_id=NULL");
        database.update("DELETE FROM media_asset");
        database.update("DELETE FROM video_comment_like");
        database.update("DELETE FROM video_comment WHERE parent_id IS NOT NULL");
        database.update("DELETE FROM video_comment");
        database.update("DELETE FROM video_like");
        database.update("DELETE FROM video_favorite");
        database.update("DELETE FROM favorite_folder_item");
        database.update("DELETE FROM favorite_folder");
        database.update("DELETE FROM view_history");
        database.update("DELETE FROM video_tag_item");
        database.update("DELETE FROM video_tag");
        database.update("DELETE FROM notification");
        database.update("DELETE FROM content_report");
        database.update("DELETE FROM video_view_daily");
        database.update("DELETE FROM video");
        database.update("DELETE FROM video_category");
        database.update("DELETE FROM user_profile");
        database.update("DELETE FROM user_follow");
        database.update("DELETE FROM video_collection_item");
        database.update("DELETE FROM video_collection");
        database.update("DELETE FROM user_account");
        registration.execute("profile_author", "SafePassword123");
        bootstrap.execute("profile_admin", "SafePassword123");
        author = actor("profile_author");
        admin = actor("profile_admin");
        categoryId = categories.create(admin, "profile", 0).id();
    }

    @Test
    void profileFieldsValidateVersionAndExposeOnAuthorAndVideos() {
        var initial = profiles.mine(author);
        assertNull(initial.displayName());
        assertEquals(0, initial.version());
        var updated = profiles.update(author, 0, "小帧", "拍一点日常", "https://example.com/author");
        assertEquals("小帧", updated.displayName());
        assertEquals(1, updated.version());
        assertEquals(ErrorCode.STALE_WRITE, failure(() -> profiles.update(author, 0, "过期", "", "")));
        for (String bad : List.of("javascript:alert(1)", "ftp://example.com", "https://user:pass@example.com", "not a url")) {
            assertEquals(ErrorCode.INVALID_PROFILE, failure(() -> profiles.update(author, updated.version(), "小帧", "", bad)));
        }
        var publicProfile = follows.profile(author, author.id());
        assertEquals("小帧", publicProfile.displayName());
        assertEquals("拍一点日常", publicProfile.bio());
        assertEquals("https://example.com/author", publicProfile.website());
        long video = published("profile video");
        assertEquals("小帧", publicVideos.get(video).authorName());
    }

    @Test
    void avatarAcceptsOnlySmallPngJpegWebpAndCanBeRemoved() {
        assertEquals(ErrorCode.INVALID_AVATAR, failure(() -> profiles.uploadAvatar(author, "plain text".getBytes())));
        assertEquals(ErrorCode.INVALID_AVATAR, failure(() -> profiles.uploadAvatar(author, new byte[200 * 1024 + 1])));
        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3};
        var saved = profiles.uploadAvatar(author, png);
        assertTrue(saved.hasAvatar());
        var avatar = profiles.avatar(author.id());
        assertEquals("image/png", avatar.contentType());
        assertArrayEquals(png, avatar.data());
        assertTrue(follows.profile(author, author.id()).hasAvatar());
        profiles.removeAvatar(author);
        assertFalse(profiles.mine(author).hasAvatar());
        assertEquals(ErrorCode.NOT_FOUND, failure(() -> profiles.avatar(author.id())));
    }

    private long published(String title) {
        long id = drafts.create(author, UUID.randomUUID().toString(), new VideoMetadata(title, "description", categoryId)).id();
        database.update("INSERT INTO media_asset (video_id,kind,object_key,size_bytes) VALUES (?,'SOURCE',?,64)", id, "sources/" + UUID.randomUUID() + ".mp4");
        long asset = database.queryForObject("SELECT id FROM media_asset WHERE video_id=?", Long.class, id);
        database.update("INSERT INTO media_asset (video_id,kind,object_key,size_bytes) VALUES (?,'COVER',?,10)", id, "covers/" + UUID.randomUUID() + ".jpg");
        database.update("UPDATE video SET source_asset_id=?,processing_status='READY',duration_millis=1000,media_width=320,media_height=240 WHERE id=?", asset, id);
        database.update("INSERT INTO processing_task (video_id,state) VALUES (?,'SUCCEEDED')", id);
        reviews.submit(author, id, 0);
        reviews.decide(admin, id, 1, 1, new ReviewDecision(ReviewDecision.Outcome.APPROVED, ""));
        return id;
    }
    private ErrorCode failure(org.junit.jupiter.api.function.Executable operation) { return assertThrows(BusinessException.class, operation).getCode(); }
    private Actor actor(String name) { var account = accounts.findSummary(name).orElseThrow(); return new Actor(account.id(), name, account.role(), account.adminScope()); }
}
