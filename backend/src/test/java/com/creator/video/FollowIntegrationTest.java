package com.creator.video;

import static org.junit.jupiter.api.Assertions.*;
import com.creator.common.BusinessException;
import com.creator.identity.application.RegisterUser;
import com.creator.identity.domain.*;
import com.creator.video.application.FollowService;
import com.creator.video.application.NotificationService;
import com.creator.common.PageRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class FollowIntegrationTest {
    @Autowired FollowService follows; @Autowired NotificationService notifications; @Autowired RegisterUser registerUser; @Autowired AccountRepository accounts; @Autowired JdbcTemplate database;
    Actor a; Actor b;
    @BeforeEach void reset() {
        database.update("DELETE FROM user_follow"); database.update("DELETE FROM notification");
        database.update("DELETE FROM content_report"); database.update("DELETE FROM operation_log");
        database.update("DELETE FROM video_comment"); database.update("DELETE FROM video_like");
        database.update("DELETE FROM video_favorite"); database.update("DELETE FROM video_collection_item");
        database.update("DELETE FROM video_collection"); database.update("DELETE FROM review_record");
        database.update("DELETE FROM processing_task"); database.update("DELETE FROM upload_attempt");
        database.update("UPDATE video SET source_asset_id=NULL"); database.update("DELETE FROM media_asset");
        database.update("DELETE FROM video"); database.update("DELETE FROM video_category");
        database.update("DELETE FROM user_account");
        registerUser.execute("follow_a", "SafePassword123"); registerUser.execute("follow_b", "SafePassword123");
        a = actor("follow_a"); b = actor("follow_b");
    }
    @Test void followsAreIdempotentAndCounted() {
        follows.follow(a, b.id()); follows.follow(a, b.id());
        assertTrue(follows.profile(a, b.id()).following());
        assertEquals(1, follows.profile(a, b.id()).followerCount());
        assertEquals(1, follows.profile(b, a.id()).followingCount());
        assertEquals(1, notifications.list(b, new PageRequest(1, 20)).total());
        assertEquals(1, notifications.unread(b));
        notifications.markAll(b);
        assertEquals(0, notifications.unread(b));
        assertThrows(BusinessException.class, () -> follows.follow(a, a.id()));
        follows.unfollow(a, b.id()); follows.unfollow(a, b.id());
        assertFalse(follows.profile(a, b.id()).following());
    }
    private Actor actor(String name) { var x = accounts.findSummary(name).orElseThrow(); return new Actor(x.id(), name, x.role()); }
}
