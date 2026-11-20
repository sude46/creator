package com.creator.video;

import com.creator.catalog.application.CategoryService;
import com.creator.common.*;
import com.creator.identity.application.*;
import com.creator.identity.domain.*;
import com.creator.video.application.*;
import com.creator.video.domain.*;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AnalyticsIntegrationTest {
    @Autowired JdbcTemplate database;
    @Autowired DraftService drafts;
    @Autowired ReviewService reviews;
    @Autowired PublicVideoService publicVideos;
    @Autowired CreatorAnalyticsService analytics;
    @Autowired FollowService follows;
    @Autowired CategoryService categories;
    @Autowired RegisterUser registration;
    @Autowired BootstrapAdmin bootstrap;
    @Autowired AccountRepository accounts;
    Actor author;
    Actor other;
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
        database.update("DELETE FROM video_view_daily");
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
        database.update("DELETE FROM video_collection_item");
        database.update("DELETE FROM video_collection");
        database.update("DELETE FROM video");
        database.update("DELETE FROM video_category");
        database.update("DELETE FROM user_follow");
        database.update("DELETE FROM user_profile");
        database.update("DELETE FROM content_appeal");
        database.update("DELETE FROM user_account");
        registration.execute("analytics_author", "SafePassword123");
        registration.execute("analytics_other", "SafePassword123");
        bootstrap.execute("analytics_admin", "SafePassword123");
        author = actor("analytics_author");
        other = actor("analytics_other");
        admin = actor("analytics_admin");
        categoryId = categories.create(admin, "analytics", 0).id();
    }

    @Test
    void totalsDailySeriesAndTopVideosAreOwnerScoped() {
        long first = published("first");
        long second = published("second");
        publicVideos.recordView(first);
        publicVideos.recordView(first);
        publicVideos.recordView(second);
        database.update("INSERT INTO video_like (user_id, video_id) VALUES (?,?)", other.id(), first);
        database.update("INSERT INTO video_favorite (user_id, video_id) VALUES (?,?)", other.id(), first);
        database.update("INSERT INTO video_comment (video_id, user_id, content) VALUES (?,?,?)", first, other.id(), "统计");
        follows.follow(other, author.id());

        var result = analytics.get(author, 7);
        assertEquals(2, result.videoCount());
        assertEquals(3, result.views());
        assertEquals(1, result.likes());
        assertEquals(1, result.favorites());
        assertEquals(1, result.comments());
        assertEquals(1, result.followers());
        assertEquals(7, result.daily().size());
        assertEquals(3, result.daily().getLast().views());
        assertEquals(LocalDate.now(ZoneOffset.UTC), result.daily().getLast().date());
        assertEquals(first, result.topVideos().getFirst().id());
        assertEquals(2, result.topVideos().getFirst().views());
        assertEquals(1, result.topVideos().getFirst().likes());

        var otherView = analytics.get(other, 7);
        assertEquals(0, otherView.videoCount());
        assertEquals(0, otherView.daily().getLast().views());
        assertEquals(ErrorCode.INVALID_PAGE, failure(() -> analytics.get(author, 0)));
        assertEquals(ErrorCode.INVALID_PAGE, failure(() -> analytics.get(author, 91)));
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
