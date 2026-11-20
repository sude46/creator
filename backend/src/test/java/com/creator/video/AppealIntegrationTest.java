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
class AppealIntegrationTest {
    @Autowired JdbcTemplate database;
    @Autowired DraftService drafts;
    @Autowired ReviewService reviews;
    @Autowired PublicationService publications;
    @Autowired AppealService appeals;
    @Autowired ReportService reports;
    @Autowired NotificationService notifications;
    @Autowired CreatorVideoRepository videos;
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
        database.update("DELETE FROM content_appeal");
        database.update("DELETE FROM content_report");
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
        database.update("DELETE FROM video_collection_item");
        database.update("DELETE FROM video_collection");
        database.update("DELETE FROM video");
        database.update("DELETE FROM video_category");
        database.update("DELETE FROM user_follow");
        database.update("DELETE FROM user_profile");
        database.update("DELETE FROM user_account");
        registration.execute("appeal_author", "SafePassword123");
        registration.execute("appeal_other", "SafePassword123");
        bootstrap.execute("appeal_admin", "SafePassword123");
        author = actor("appeal_author");
        other = actor("appeal_other");
        admin = actor("appeal_admin");
        categoryId = categories.create(admin, "appeal", 0).id();
    }

    @Test
    void blockedVideoAppealIsOwnerScopedAndAcceptedAppealUnblocks() {
        long video = blocked("申诉作品");
        assertEquals(ErrorCode.NOT_FOUND, failure(() -> appeals.create(other, "VIDEO", video, "不是我的作品")));
        var appeal = appeals.create(author, "video", video, "这是正常内容，请复核");
        assertEquals("OPEN", appeal.status());
        assertEquals(1, appeals.mine(author, "VIDEO", video, new PageRequest(1, 20)).total());
        assertEquals(ErrorCode.APPEAL_ALREADY_OPEN, failure(() -> appeals.create(author, "VIDEO", video, "重复申诉")));

        var adminPage = appeals.adminList(admin, "OPEN", new PageRequest(1, 20));
        assertEquals(1, adminPage.total());
        appeals.resolve(admin, appeal.id(), "ACCEPT", "复核后恢复");
        assertFalse(videos.findAny(video, false).orElseThrow().moderationBlocked());
        assertEquals("ACCEPTED", appeals.mine(author, "VIDEO", video, new PageRequest(1, 20)).items().getFirst().status());
        assertEquals(ErrorCode.STALE_WRITE, failure(() -> appeals.resolve(admin, appeal.id(), "REJECT", "重复处理")));
        assertEquals(0, appeals.adminList(admin, "OPEN", new PageRequest(1, 20)).total());
        var notice = notifications.list(author, false, "APPEAL", new PageRequest(1, 20));
        assertEquals(1, notice.total());
        assertEquals("申诉已受理", notice.items().getFirst().title());
    }

    @Test
    void rejectedAppealKeepsBlockAndOnlyModeratorsCanResolve() {
        long video = blocked("驳回申诉");
        var appeal = appeals.create(author, "VIDEO", video, "请求恢复");
        var reviewer = new Actor(admin.id(), admin.username(), Role.ADMIN, AdminScope.REVIEWER);
        assertEquals(ErrorCode.FORBIDDEN, failure(() -> appeals.resolve(reviewer, appeal.id(), "ACCEPT", "越权")));
        appeals.resolve(admin, appeal.id(), "REJECT", "内容仍不符合规范");
        assertTrue(videos.findAny(video, false).orElseThrow().moderationBlocked());
        assertEquals("REJECTED", appeals.mine(author, "VIDEO", video, new PageRequest(1, 20)).items().getFirst().status());
        assertEquals(1, appeals.adminList(admin, "REJECTED", new PageRequest(1, 20)).total());
        assertEquals("OPEN", appeals.create(author, "VIDEO", video, "再次申诉").status());
        assertEquals("申诉已驳回", notifications.list(author, false, "APPEAL", new PageRequest(1, 20)).items().getFirst().title());
    }

    @Test
    void reportResolutionNotifiesTheReporter() {
        long video = published("举报通知");
        reports.report(other, "VIDEO", video, "垃圾内容");
        var report = reports.list(admin, "OPEN", new PageRequest(1, 20)).items().getFirst();
        reports.resolve(admin, report.id(), "DISMISS", "未发现违规");
        var notice = notifications.list(other, false, "REPORT", new PageRequest(1, 20));
        assertEquals(1, notice.total());
        assertEquals("举报已处理：未发现违规", notice.items().getFirst().title());
        assertEquals("未发现违规", notice.items().getFirst().body());
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

    private long blocked(String title) {
        long id = published(title);
        var current = videos.findAny(id, false).orElseThrow();
        publications.block(admin, id, current.version(), new ModerationReason("测试封禁"));
        return id;
    }
    private ErrorCode failure(org.junit.jupiter.api.function.Executable operation) { return assertThrows(BusinessException.class, operation).getCode(); }
    private Actor actor(String name) { var account = accounts.findSummary(name).orElseThrow(); return new Actor(account.id(), name, account.role(), account.adminScope()); }
}
