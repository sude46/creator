package com.creator.admin;

import static org.junit.jupiter.api.Assertions.*;

import com.creator.admin.application.AdminOverviewService;
import com.creator.catalog.application.CategoryService;
import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.identity.application.BootstrapAdmin;
import com.creator.identity.application.RegisterUser;
import com.creator.identity.domain.AccountRepository;
import com.creator.identity.domain.Actor;
import com.creator.video.application.DraftService;
import com.creator.video.domain.VideoMetadata;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AdminOverviewIntegrationTest {
    @Autowired AdminOverviewService overview;
    @Autowired RegisterUser registerUser;
    @Autowired BootstrapAdmin bootstrapAdmin;
    @Autowired AccountRepository accounts;
    @Autowired CategoryService categories;
    @Autowired DraftService drafts;
    @Autowired JdbcTemplate database;
    Actor admin;
    Actor author;
    long categoryId;

    @BeforeEach
    void reset() {
        database.update("DELETE FROM operation_log");
        database.update("DELETE FROM review_record");
        database.update("DELETE FROM video_deletion");
        database.update("DELETE FROM processing_task");
        database.update("DELETE FROM upload_attempt");
        database.update("UPDATE video SET source_asset_id = NULL");
        database.update("DELETE FROM media_asset");
        database.update("DELETE FROM video_favorite");
        database.update("DELETE FROM video_comment");
        database.update("DELETE FROM favorite_folder_item");
        database.update("DELETE FROM favorite_folder");
        database.update("DELETE FROM view_history");
        database.update("DELETE FROM video_tag_item");
        database.update("DELETE FROM video_tag");
        database.update("DELETE FROM video_comment");
        database.update("DELETE FROM video_like");
        database.update("DELETE FROM video_favorite");
        database.update("DELETE FROM video");
        database.update("DELETE FROM video_category");
        database.update("DELETE FROM notification");
        database.update("DELETE FROM content_report");
        database.update("DELETE FROM user_account");
        registerUser.execute("overview_author", "SafePassword123");
        bootstrapAdmin.execute("overview_admin", "SafePassword123");
        admin = actor("overview_admin");
        author = actor("overview_author");
        categoryId = categories.create(admin, "概览分类", 0).id();
    }

    @Test
    void reportsRealCountsAndRejectsNonAdmin() {
        long pending = drafts.create(author, UUID.randomUUID().toString(), new VideoMetadata("待审", "", categoryId)).id();
        database.update("UPDATE video SET review_status = 'PENDING', review_submitted_at = CURRENT_TIMESTAMP(6) WHERE id = ?", pending);
        long failed = drafts.create(author, UUID.randomUUID().toString(), new VideoMetadata("失败", "", categoryId)).id();
        database.update("UPDATE video SET processing_status = 'FAILED' WHERE id = ?", failed);
        database.update("""
                INSERT INTO review_record
                    (video_id, review_round, content_version, processing_generation, reviewer_id, outcome, reason,
                     title, description, category_id, created_at)
                VALUES (?, 1, 1, 1, ?, 'APPROVED', '通过', '待审', '', ?, CURRENT_TIMESTAMP(6))
                """, pending, admin.id(), categoryId);

        var result = overview.get(admin);
        assertEquals(1, result.pendingReviews());
        assertEquals(1, result.failedProcessing());
        assertEquals(2, result.todayUsers());
        assertEquals(1, result.todayDecisions());
        assertEquals(1, result.recentDecisions().size());
        assertEquals(pending, result.recentDecisions().getFirst().videoId());
        assertEquals("APPROVED", result.recentDecisions().getFirst().outcome());

        var denied = assertThrows(BusinessException.class, () -> overview.get(author));
        assertEquals(ErrorCode.FORBIDDEN, denied.getCode());
    }

    private Actor actor(String username) {
        var account = accounts.findSummary(username).orElseThrow();
        return new Actor(account.id(), username, account.role());
    }
}
