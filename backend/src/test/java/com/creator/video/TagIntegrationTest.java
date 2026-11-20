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
class TagIntegrationTest {
    @Autowired JdbcTemplate database;
    @Autowired DraftService drafts;
    @Autowired ReviewService reviews;
    @Autowired PublicVideoService publicVideos;
    @Autowired CreatorVideoService creatorVideos;
    @Autowired TagService tagService;
    @Autowired TagRepository tags;
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
        database.update("DELETE FROM favorite_folder_item");
        database.update("DELETE FROM favorite_folder");
        database.update("DELETE FROM view_history");
        database.update("DELETE FROM video_tag_item");
        database.update("DELETE FROM video_tag");
        database.update("DELETE FROM video_comment_like");
        database.update("DELETE FROM video_comment WHERE parent_id IS NOT NULL");
        database.update("DELETE FROM video_comment");
        database.update("DELETE FROM video_like");
        database.update("DELETE FROM video_favorite");
        database.update("DELETE FROM video_view_daily");
        database.update("DELETE FROM video");
        database.update("DELETE FROM video_category");
        database.update("DELETE FROM notification");
        database.update("DELETE FROM content_report");
        database.update("DELETE FROM user_profile");
        database.update("DELETE FROM user_follow");
        database.update("DELETE FROM video_collection_item");
        database.update("DELETE FROM video_collection");
        database.update("DELETE FROM user_account");
        registration.execute("tag_author", "SafePassword123");
        bootstrap.execute("tag_admin", "SafePassword123");
        author = actor("tag_author");
        admin = actor("tag_admin");
        categoryId = categories.create(admin, "tag", 0).id();
    }

    @Test
    void tagsPersistThroughEditsAndFilterOnlyPublicWorks() {
        long id = ready("tagged work");
        var updated = reviews.update(author, id, 0,
                new VideoMetadata("tagged work", "description", categoryId, List.of("后端", "Spring")));
        assertEquals(1, updated.version());
        assertEquals(List.of("Spring", "后端"), tagService.ownerTags(author, id));
        assertEquals(0, publicVideos.list(new VideoSearch("", null, "后端"), new PageRequest(1, 20)).total());
        assertTrue(tagService.popular(20).isEmpty());

        reviews.submit(author, id, updated.version());
        reviews.decide(admin, id, 2, 1, new ReviewDecision(ReviewDecision.Outcome.APPROVED, ""));
        assertEquals(1, publicVideos.list(new VideoSearch("", null, "后端"), new PageRequest(1, 20)).total());
        assertEquals(1, publicVideos.list(new VideoSearch("", null, "#spring"), new PageRequest(1, 20)).total());
        assertEquals(1, publicVideos.list(new VideoSearch("", categoryId, "后端"), new PageRequest(1, 20)).total());
        assertEquals(0, publicVideos.list(new VideoSearch("", null, "前端"), new PageRequest(1, 20)).total());
        assertEquals(List.of(new TagCount("Spring", 1), new TagCount("后端", 1)), tagService.popular(20));

        long changed = reviews.update(author, id, creatorVideos.get(author, id).version(),
                new VideoMetadata("tagged work", "description", categoryId, List.of("前端"))).version();
        assertEquals(0, publicVideos.list(new VideoSearch("", null, "后端"), new PageRequest(1, 20)).total());
        assertEquals(List.of("前端"), tags.tags(id));
        assertEquals(4, changed);
    }

    @Test
    void tagValidationRejectsDuplicatesLimitsAndUnsafeCharacters() {
        assertEquals(ErrorCode.INVALID_TAG, failure(() -> new VideoMetadata("t", "", categoryId,
                List.of("one", "two", "three", "four", "five", "six"))));
        assertEquals(ErrorCode.INVALID_TAG, failure(() -> new VideoMetadata("t", "", categoryId, List.of("dup", "DUP"))));
        assertEquals(ErrorCode.INVALID_TAG, failure(() -> new VideoMetadata("t", "", categoryId, List.of("a".repeat(21)))));
        assertEquals(ErrorCode.INVALID_TAG, failure(() -> new VideoMetadata("t", "", categoryId, List.of("bad,tag"))));
        assertEquals(ErrorCode.INVALID_TAG, failure(() -> new VideoMetadata("t", "", categoryId, List.of("bad\u0000tag"))));
        assertEquals(ErrorCode.INVALID_TAG, failure(() -> new VideoMetadata("t", "", categoryId, List.of("  "))));
        assertEquals(ErrorCode.INVALID_TAG, failure(() -> new VideoSearch("", null, "bad,tag")));
        assertEquals(List.of("标签"), new VideoMetadata("t", "", categoryId, List.of(" #标签 ")).tags());
    }

    @Test
    void rejectedEditDoesNotChangeStoredTags() {
        long id = ready("stable");
        reviews.update(author, id, 0, new VideoMetadata("stable", "", categoryId, List.of("keep")));
        assertEquals(ErrorCode.STALE_WRITE, failure(() -> reviews.update(author, id, 0,
                new VideoMetadata("stable", "", categoryId, List.of("changed")))));
        assertEquals(List.of("keep"), tags.tags(id));
    }

    private long ready(String title) {
        long id = drafts.create(author, UUID.randomUUID().toString(), new VideoMetadata(title, "description", categoryId)).id();
        database.update("INSERT INTO media_asset (video_id,kind,object_key,size_bytes) VALUES (?,'SOURCE',?,64)", id, "sources/" + UUID.randomUUID() + ".mp4");
        long asset = database.queryForObject("SELECT id FROM media_asset WHERE video_id=?", Long.class, id);
        database.update("INSERT INTO media_asset (video_id,kind,object_key,size_bytes) VALUES (?,'COVER',?,10)", id, "covers/" + UUID.randomUUID() + ".jpg");
        database.update("UPDATE video SET source_asset_id=?,processing_status='READY',duration_millis=1000,media_width=320,media_height=240 WHERE id=?", asset, id);
        database.update("INSERT INTO processing_task (video_id,state) VALUES (?,'SUCCEEDED')", id);
        return id;
    }
    private ErrorCode failure(java.util.concurrent.Callable<?> operation) { return assertThrows(BusinessException.class, operation::call).getCode(); }
    private Actor actor(String name) { var account = accounts.findSummary(name).orElseThrow(); return new Actor(account.id(), name, account.role()); }
}
