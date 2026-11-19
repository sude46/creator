package com.creator.audit;

import static org.junit.jupiter.api.Assertions.*;

import com.creator.audit.application.AuditLog;
import com.creator.audit.application.AuditQueryService;
import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.common.PageRequest;
import com.creator.identity.application.BootstrapAdmin;
import com.creator.identity.application.RegisterUser;
import com.creator.identity.domain.AccountRepository;
import com.creator.identity.domain.Actor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AuditQueryIntegrationTest {
    @Autowired AuditLog audit;
    @Autowired AuditQueryService query;
    @Autowired RegisterUser registerUser;
    @Autowired BootstrapAdmin bootstrapAdmin;
    @Autowired AccountRepository accounts;
    @Autowired JdbcTemplate database;
    Actor admin;
    Actor author;

    @BeforeEach
    void reset() {
        database.update("DELETE FROM operation_log");
        database.update("DELETE FROM video_deletion");
        database.update("DELETE FROM review_record");
        database.update("DELETE FROM processing_task");
        database.update("DELETE FROM upload_attempt");
        database.update("UPDATE video SET source_asset_id = NULL");
        database.update("DELETE FROM media_asset");
        database.update("DELETE FROM video_favorite");
        database.update("DELETE FROM video_comment");
        database.update("DELETE FROM video");
        database.update("DELETE FROM video_category");
        database.update("DELETE FROM notification");
        database.update("DELETE FROM user_account");
        registerUser.execute("audit_author", "SafePassword123");
        bootstrapAdmin.execute("audit_admin", "SafePassword123");
        admin = actor("audit_admin");
        author = actor("audit_author");
        audit.record(admin, "VIDEO_DELETED", "VIDEO", 42, "policy removal");
        audit.record(admin, "USER_DISABLED", "USER", 7, null);
    }

    @Test
    void adminFiltersAuditAndNonAdminIsForbidden() {
        var page = query.list(admin, null, "VIDEO_DELETED", "VIDEO", null, null, new PageRequest(1, 20));
        assertEquals(1, page.total());
        assertEquals("policy removal", page.items().getFirst().reason());
        var all = query.export(admin, null, null, null, null, null);
        assertEquals(2, all.size());
        var forbidden = assertThrows(BusinessException.class,
                () -> query.list(author, null, null, null, null, null, new PageRequest(1, 20)));
        assertEquals(ErrorCode.FORBIDDEN, forbidden.getCode());
    }

    private Actor actor(String username) {
        var account = accounts.findSummary(username).orElseThrow();
        return new Actor(account.id(), username, account.role());
    }
}
