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
class CommentInteractionIntegrationTest {
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
        registration.execute("comment_author", "SafePassword123");
        registration.execute("comment_other", "SafePassword123");
        bootstrap.execute("comment_admin", "SafePassword123");
        author = actor("comment_author");
        other = actor("comment_other");
        admin = actor("comment_admin");
        categoryId = categories.create(admin, "comment", 0).id();
    }

    @Test
    void repliesFlattenUnderTheRootAndNotifyTheParentAuthor() {
        long video = published("reply target");
        var top = comments.add(author, video, "顶层评论");
        var reply = comments.add(other, video, "回复顶层", top.id());
        var nested = comments.add(author, video, "回复回复", reply.id());
        assertEquals(top.id(), reply.parentId());
        assertEquals(top.id(), nested.parentId());

        var page = comments.list(author, video, new PageRequest(1, 10));
        assertEquals(1, page.total());
        assertEquals(2, page.items().getFirst().replyCount());
        assertEquals(2, comments.replies(author, top.id(), new PageRequest(1, 50)).total());
        assertTrue(notifications.list(author, new PageRequest(1, 20)).items().stream()
                .anyMatch(item -> item.title().equals("comment_other 回复了你的评论")));

        assertEquals(ErrorCode.FORBIDDEN, failure(() -> comments.delete(other, top.id())));
        comments.delete(author, top.id());
        assertEquals(0, comments.list(author, video, new PageRequest(1, 10)).total());
        assertEquals(ErrorCode.NOT_FOUND, failure(() -> comments.replies(author, top.id(), new PageRequest(1, 50))));
    }

    @Test
    void likesAreIdempotentAndScopedToPublicVideos() {
        long video = published("like target");
        var comment = comments.add(author, video, "点赞我");
        comments.like(other, comment.id());
        comments.like(other, comment.id());
        var liked = comments.list(other, video, new PageRequest(1, 10)).items().getFirst();
        assertEquals(1, liked.likeCount());
        assertTrue(liked.liked());
        assertFalse(comments.list(author, video, new PageRequest(1, 10)).items().getFirst().liked());
        comments.unlike(other, comment.id());
        assertEquals(0, comments.list(other, video, new PageRequest(1, 10)).items().getFirst().likeCount());

        long otherVideo = published("other target");
        var foreign = comments.add(author, otherVideo, "别的作品");
        assertEquals(ErrorCode.INVALID_REQUEST, failure(() -> comments.add(other, video, "错位回复", foreign.id())));
        database.update("UPDATE video SET publication_status='OFFLINE' WHERE id=?", video);
        assertEquals(ErrorCode.NOT_FOUND, failure(() -> comments.like(other, comment.id())));
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
