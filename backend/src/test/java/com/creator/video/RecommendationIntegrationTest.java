package com.creator.video;

import com.creator.catalog.application.CategoryService;
import com.creator.common.*;
import com.creator.identity.application.*;
import com.creator.identity.domain.*;
import com.creator.video.application.*;
import com.creator.video.domain.*;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class RecommendationIntegrationTest {
    @Autowired JdbcTemplate database;
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
        database.update("DELETE FROM content_appeal");
        database.update("DELETE FROM user_account");
        registration.execute("rec_author", "SafePassword123");
        registration.execute("rec_like_a", "SafePassword123");
        registration.execute("rec_like_b", "SafePassword123");
        registration.execute("rec_like_c", "SafePassword123");
        bootstrap.execute("rec_admin", "SafePassword123");
        author = actor("rec_author");
        admin = actor("rec_admin");
        categoryId = categories.create(admin, "rec", 0).id();
    }

    @Test
    void recommendedBalancesEngagementAgainstAge() {
        long engaged = published("engaged");
        long popular = published("popular");
        long recent = published("recent");
        long oldViral = published("old viral");
        for (String user : List.of("rec_like_a", "rec_like_b", "rec_like_c")) {
            database.update("INSERT INTO video_like (user_id, video_id) VALUES (?,?)", actor(user).id(), engaged);
        }
        database.update("INSERT INTO video_favorite (user_id, video_id) VALUES (?,?)", actor("rec_like_a").id(), engaged);
        database.update("INSERT INTO video_comment (video_id, user_id, content) VALUES (?,?,?)", engaged, actor("rec_like_a").id(), "好");
        var now = LocalDateTime.now(java.time.ZoneOffset.UTC);
        database.update("UPDATE video SET view_count=5, published_at=? WHERE id=?", now.minusHours(1), popular);
        database.update("UPDATE video SET view_count=0, published_at=? WHERE id=?", now.minusHours(3), recent);
        database.update("UPDATE video SET view_count=100, published_at=? WHERE id=?", now.minusHours(100), oldViral);

        var page = publicVideos.list(new VideoSearch("", null), new PageRequest(1, 12), "recommended");
        assertEquals(List.of(engaged, popular, recent, oldViral), page.items().stream().map(PublicVideo::id).toList());
        assertEquals(engaged, publicVideos.list(new VideoSearch("", null), new PageRequest(1, 1), "recommended")
                .items().getFirst().id());
        assertEquals(popular, publicVideos.list(new VideoSearch("", null), new PageRequest(2, 1), "recommended")
                .items().getFirst().id());
        assertEquals(ErrorCode.INVALID_REQUEST, failure(() -> publicVideos.list(new VideoSearch("", null),
                new PageRequest(1, 12), "random")));
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
