package com.creator.identity;

import com.creator.audit.application.AuditQueryService;
import com.creator.catalog.application.CategoryService;
import com.creator.common.*;
import com.creator.identity.application.*;
import com.creator.identity.domain.*;
import com.creator.video.application.ReportService;
import com.creator.video.application.ReviewService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AdminScopeIntegrationTest {
    @Autowired JdbcTemplate database;
    @Autowired RegisterUser registration;
    @Autowired BootstrapAdmin bootstrap;
    @Autowired AdminUserService adminUsers;
    @Autowired AccountQueryService accounts;
    @Autowired ReviewService reviews;
    @Autowired ReportService reports;
    @Autowired AuditQueryService audit;
    @Autowired CategoryService categories;

    @BeforeEach
    void reset() {
        database.update("DELETE FROM operation_log");
        database.update("DELETE FROM notification");
        database.update("DELETE FROM content_report");
        database.update("DELETE FROM user_follow");
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
        database.update("DELETE FROM review_record");
        database.update("DELETE FROM processing_task");
        database.update("DELETE FROM upload_attempt");
        database.update("UPDATE video SET source_asset_id=NULL");
        database.update("DELETE FROM media_asset");
        database.update("DELETE FROM video_view_daily");
        database.update("DELETE FROM video");
        database.update("DELETE FROM video_category");
        database.update("DELETE FROM user_profile");
        database.update("DELETE FROM video_collection_item");
        database.update("DELETE FROM video_collection");
        database.update("DELETE FROM content_appeal");
        database.update("DELETE FROM user_account");
        bootstrap.execute("scope_admin", "SafePassword123");
        registration.execute("scope_reviewer", "SafePassword123");
        registration.execute("scope_moderator", "SafePassword123");
        registration.execute("scope_regular", "SafePassword123");
        var superAdmin = accounts.current("scope_admin");
        adminUsers.setAdminScope(superAdmin, accounts.current("scope_reviewer").id(), "REVIEWER");
        adminUsers.setAdminScope(superAdmin, accounts.current("scope_moderator").id(), "MODERATOR");
    }

    @Test
    void reviewerAndModeratorGetSeparatedAdminCapabilities() {
        var reviewer = accounts.current("scope_reviewer");
        var moderator = accounts.current("scope_moderator");
        assertEquals(AdminScope.REVIEWER, reviewer.adminScope());
        assertTrue(reviewer.canReview());
        assertFalse(reviewer.canModerate());
        assertEquals(AdminScope.MODERATOR, moderator.adminScope());
        assertTrue(moderator.canModerate());
        assertFalse(moderator.canReview());

        assertDoesNotThrow(() -> reviews.queue(reviewer, new PageRequest(1, 20)));
        assertDoesNotThrow(() -> reports.list(moderator, null, new PageRequest(1, 20)));
        assertEquals(ErrorCode.FORBIDDEN, failure(() -> reviews.queue(moderator, new PageRequest(1, 20))));
        assertEquals(ErrorCode.FORBIDDEN, failure(() -> reports.list(reviewer, null, new PageRequest(1, 20))));
        assertEquals(ErrorCode.FORBIDDEN, failure(() -> audit.list(reviewer, null, null, null, null, null, new PageRequest(1, 20))));
        assertEquals(ErrorCode.FORBIDDEN, failure(() -> categories.create(reviewer, "越权分类", 0)));
        assertEquals(ErrorCode.FORBIDDEN, failure(() -> reviews.queue(accounts.current("scope_regular"), new PageRequest(1, 20))));
    }

    @Test
    void onlySuperAdminAssignsScopesAndCannotChangeSelf() {
        var superAdmin = accounts.current("scope_admin");
        var reviewer = accounts.current("scope_reviewer");
        var moderator = accounts.current("scope_moderator");
        assertEquals(ErrorCode.FORBIDDEN, failure(() -> adminUsers.setAdminScope(moderator, reviewer.id(), "NONE")));
        assertEquals(ErrorCode.FORBIDDEN, failure(() -> adminUsers.setAdminScope(reviewer, reviewer.id(), "NONE")));
        assertEquals(ErrorCode.FORBIDDEN, failure(() -> adminUsers.setAdminScope(superAdmin, superAdmin.id(), "REVIEWER")));
        assertEquals(ErrorCode.FORBIDDEN, failure(() -> adminUsers.setAdminScope(superAdmin, reviewer.id(), "SUPER_ADMIN")));
        var listed = adminUsers.list(superAdmin, new PageRequest(1, 20));
        assertTrue(listed.items().stream().anyMatch(item -> item.username().equals("scope_reviewer")
                && item.adminScope() == AdminScope.REVIEWER));
        var demoted = adminUsers.setAdminScope(superAdmin, reviewer.id(), "NONE");
        assertEquals(Role.USER, demoted.role());
        assertNull(demoted.adminScope());
        assertEquals(ErrorCode.FORBIDDEN, failure(() -> reviews.queue(accounts.current("scope_reviewer"), new PageRequest(1, 20))));
        assertDoesNotThrow(() -> adminUsers.setEnabled(superAdmin, moderator.id(), false));
    }

    private ErrorCode failure(org.junit.jupiter.api.function.Executable operation) {
        return assertThrows(BusinessException.class, operation).getCode();
    }
}
