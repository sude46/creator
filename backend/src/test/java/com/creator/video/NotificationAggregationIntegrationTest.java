package com.creator.video;

import com.creator.catalog.application.CategoryService;
import com.creator.common.*;
import com.creator.identity.application.*;
import com.creator.identity.domain.*;
import com.creator.video.application.*;
import com.creator.video.domain.*;
import java.time.LocalDateTime;
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
class NotificationAggregationIntegrationTest {
    @Autowired JdbcTemplate database;
    @Autowired DraftService drafts;
    @Autowired ReviewService reviews;
    @Autowired CommentService comments;
    @Autowired NotificationService notifications;
    @Autowired CategoryService categories;
    @Autowired RegisterUser registration;
    @Autowired BootstrapAdmin bootstrap;
    @Autowired AccountRepository accounts;
    Actor author;
    Actor other;
    Actor third;
    Actor admin;
    long categoryId;

    @BeforeEach
    void reset() {
        database.update("DELETE FROM operation_log");
        database.update("DELETE FROM content_appeal");
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
        database.update("DELETE FROM user_account");
        registration.execute("agg_author", "SafePassword123");
        registration.execute("agg_other", "SafePassword123");
        registration.execute("agg_third", "SafePassword123");
        bootstrap.execute("agg_admin", "SafePassword123");
        author = actor("agg_author");
        other = actor("agg_other");
        third = actor("agg_third");
        admin = actor("agg_admin");
        categoryId = categories.create(admin, "aggregate", 0).id();
    }

    @Test
    void commentsOnOneVideoAggregateAndResetAfterRead() {
        long video = published("aggregate comments");
        comments.add(other, video, "一");
        comments.add(third, video, "二");
        var list = notifications.list(author, false, "COMMENT", new PageRequest(1, 20));
        assertEquals(1, list.total());
        assertEquals("2 条新评论", list.items().getFirst().title());
        assertEquals("最新来自 agg_third", list.items().getFirst().body());

        notifications.markAll(author);
        comments.add(other, video, "三");
        assertEquals(2, notifications.list(author, false, "COMMENT", new PageRequest(1, 20)).total());
        assertEquals(1, notifications.unread(author));

        comments.add(third, video, "四");
        assertEquals(2, notifications.list(author, false, "COMMENT", new PageRequest(1, 20)).total());

        long second = published("another video");
        comments.add(third, second, "另一个作品");
        assertEquals(3, notifications.list(author, false, "COMMENT", new PageRequest(1, 20)).total());
    }

    @Test
    void repliesAggregateSeparatelyFromTopLevelComments() {
        long video = published("reply aggregate");
        var root = comments.add(author, video, "根评论");
        comments.add(other, video, "回复一", root.id());
        comments.add(third, video, "回复二", root.id());
        var list = notifications.list(author, false, "COMMENT", new PageRequest(1, 20));
        assertEquals(1, list.total());
        assertEquals("2 条新回复", list.items().getFirst().title());
        assertEquals("最新来自 agg_third", list.items().getFirst().body());
        assertEquals("COMMENT:" + video + ":reply", database.queryForObject(
                "SELECT aggregate_key FROM notification WHERE user_id=? AND type='COMMENT'", String.class, author.id()));
    }

    @Test
    void commentMentionsNotifyOnlyNewActiveUsersAndRespectReplyRecipients() {
        long video = published("mention video");
        comments.add(other, video, "感谢 @agg_author @agg_third @agg_third @missing_user @agg_other");
        var mentioned = notifications.list(third, false, "MENTION", new PageRequest(1, 20));
        assertEquals(1, mentioned.total());
        assertEquals("agg_other 在评论中提到了你", mentioned.items().getFirst().title());
        assertEquals(0, notifications.list(author, false, "MENTION", new PageRequest(1, 20)).total());
        assertEquals(0, notifications.list(other, false, "MENTION", new PageRequest(1, 20)).total());
        assertEquals(1, notifications.list(author, false, "COMMENT", new PageRequest(1, 20)).total());

        var root = comments.add(other, video, "根评论");
        comments.add(third, video, "@agg_other 回复你", root.id());
        assertEquals(1, notifications.list(other, false, "COMMENT", new PageRequest(1, 20)).total());
        assertEquals(0, notifications.list(other, false, "MENTION", new PageRequest(1, 20)).total());
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
    private Actor actor(String name) { var account = accounts.findSummary(name).orElseThrow(); return new Actor(account.id(), name, account.role(), account.adminScope()); }
}
