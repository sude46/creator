package com.creator.video;

import com.creator.audit.application.AuditLog;
import com.creator.audit.infrastructure.MyBatisAuditLog;
import com.creator.catalog.application.CategoryService;
import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.common.PageRequest;
import com.creator.identity.application.BootstrapAdmin;
import com.creator.identity.application.RegisterUser;
import com.creator.identity.domain.AccountRepository;
import com.creator.identity.domain.Actor;
import com.creator.video.application.DraftService;
import com.creator.video.domain.VideoDraft;
import com.creator.video.domain.VideoMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(DraftIntegrationTest.AuditFailureConfiguration.class)
class DraftIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate database;
    @Autowired DraftService drafts;
    @Autowired CategoryService categories;
    @Autowired RegisterUser registerUser;
    @Autowired BootstrapAdmin bootstrapAdmin;
    @Autowired AccountRepository accounts;
    @Autowired ObjectMapper json;
    @Autowired FailingAudit audit;
    Actor author;
    Actor other;
    Actor admin;
    long categoryId;

    @BeforeEach
    void reset() {
        audit.fail = false;
        database.update("DELETE FROM operation_log");
        database.update("DELETE FROM review_record");
        database.update("DELETE FROM processing_task");
        database.update("DELETE FROM upload_attempt");
        database.update("UPDATE video SET source_asset_id = NULL");
        database.update("DELETE FROM media_asset");
        database.update("DELETE FROM video_favorite");
        database.update("DELETE FROM video");
        database.update("DELETE FROM video_category");
        database.update("DELETE FROM user_account");
        registerUser.execute("draft_author", "SafePassword123");
        registerUser.execute("draft_other", "SafePassword123");
        bootstrapAdmin.execute("draft_admin", "SafePassword123");
        author = actor("draft_author");
        other = actor("draft_other");
        admin = actor("draft_admin");
        categoryId = categories.create(admin, "短剧", 0).id();
    }

    @Test
    void httpCreationReadUpdateAndListUseStringIdsAndCurrentActor() throws Exception {
        var session = login(author);
        var response = mvc.perform(post("/api/creator/drafts").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(createBody(UUID.randomUUID().toString(), " 我的作品 ")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.categoryId").value(Long.toString(categoryId)))
                .andExpect(jsonPath("$.title").value("我的作品"))
                .andExpect(jsonPath("$.creationFingerprint").doesNotExist()).andReturn();
        long id = json.readTree(response.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(get("/api/creator/drafts/{id}", id).session(session)).andExpect(status().isOk());
        mvc.perform(put("/api/creator/drafts/{id}", id).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(updateBody("新标题", categoryId, 0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(get("/api/creator/drafts").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].title").value("新标题"));
        assertEquals(author.id(), database.queryForObject("SELECT owner_id FROM video WHERE id = ?", Long.class, id));
    }

    @Test
    void otherAuthorsAndAdminsCannotReadOrModifyPrivateDrafts() throws Exception {
        var draft = create("Private");
        for (Actor outsider : List.of(other, admin)) {
            var session = login(outsider);
            mvc.perform(get("/api/creator/drafts/{id}", draft.id()).session(session))
                    .andExpect(status().isNotFound());
            mvc.perform(put("/api/creator/drafts/{id}", draft.id()).session(session).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(updateBody("stolen", categoryId, 0)))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/api/creator/drafts").session(session)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(0));
            assertEquals(ErrorCode.NOT_FOUND, assertThrows(BusinessException.class,
                    () -> drafts.update(outsider, draft.id(), 0, metadata("stolen"))).getCode());
        }
        mvc.perform(get("/api/creator/drafts")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/creator/drafts").session(login(author)).contentType(MediaType.APPLICATION_JSON)
                .content(createBody(UUID.randomUUID().toString(), "missing csrf"))).andExpect(status().isForbidden());
        assertEquals("Private", drafts.get(author, draft.id()).metadata().title());
    }

    @Test
    void requestKeysAreScopedToOwnerAndKeepOriginalFingerprintAfterEdits() throws Exception {
        String key = UUID.randomUUID().toString();
        var original = drafts.create(author, key, metadata("Original"));
        assertEquals(original.id(), drafts.create(author, key.toUpperCase(), metadata(" Original ")).id());
        var independent = drafts.create(other, key, metadata("Original"));
        assertNotEquals(original.id(), independent.id());
        drafts.update(author, original.id(), 0, metadata("Edited"));
        var replay = drafts.create(author, key, metadata("Original"));
        assertEquals(original.id(), replay.id());
        assertEquals("Edited", replay.metadata().title());
        assertEquals(ErrorCode.REQUEST_KEY_CONFLICT, assertThrows(BusinessException.class,
                () -> drafts.create(author, key, metadata("Edited"))).getCode());
        mvc.perform(post("/api/creator/drafts").session(login(author)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(createBody(key, "Different")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REQUEST_KEY_CONFLICT"));
        assertEquals(2L, database.queryForObject("SELECT COUNT(*) FROM video", Long.class));
        assertEquals(2L, database.queryForObject("SELECT COUNT(*) FROM operation_log WHERE action = 'DRAFT_CREATED'", Long.class));
    }

    @Test
    void concurrentDuplicateCreationReturnsOneDraftAndOneCreationAudit() throws Exception {
        String key = UUID.randomUUID().toString();
        var results = race(() -> drafts.create(author, key, metadata("Original")),
                () -> drafts.create(author, key, metadata("Original")));
        assertEquals(results.get(0).id(), results.get(1).id());
        assertEquals(1L, database.queryForObject("SELECT COUNT(*) FROM video", Long.class));
        assertEquals(1L, database.queryForObject("SELECT COUNT(*) FROM operation_log WHERE action = 'DRAFT_CREATED'", Long.class));
    }

    @Test
    void concurrentDifferentPayloadWithSameKeyConflicts() throws Exception {
        String key = UUID.randomUUID().toString();
        var results = race(() -> tryCreate(key, "First"), () -> tryCreate(key, "Second"));
        assertEquals(1, results.stream().filter("OK"::equals).count());
        assertEquals(1, results.stream().filter("REQUEST_KEY_CONFLICT"::equals).count());
        assertEquals(1L, database.queryForObject("SELECT COUNT(*) FROM video", Long.class));
    }

    @Test
    void versionsPreventConcurrentAndSequentialLostUpdates() throws Exception {
        var draft = create("Original");
        var results = race(() -> tryUpdate(draft, "First"), () -> tryUpdate(draft, "Second"));
        assertEquals(1, results.stream().filter("OK"::equals).count());
        assertEquals(1, results.stream().filter("STALE_WRITE"::equals).count());
        mvc.perform(put("/api/creator/drafts/{id}", draft.id()).session(login(author)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(updateBody("Stale", categoryId, 0)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));
        assertEquals(1L, drafts.get(author, draft.id()).version());
        assertEquals(1L, database.queryForObject("SELECT COUNT(*) FROM operation_log WHERE action = 'DRAFT_UPDATED'", Long.class));
    }

    @Test
    void disabledCategoriesBlockNewSelectionButAllowExistingMetadataAndReplay() {
        String key = UUID.randomUUID().toString();
        var draft = drafts.create(author, key, metadata("Original"));
        categories.update(admin, categoryId, 0, "短剧", 0, false);
        assertEquals(draft.id(), drafts.create(author, key, metadata("Original")).id());
        assertEquals(ErrorCode.CATEGORY_UNAVAILABLE, assertThrows(BusinessException.class,
                () -> create("New")).getCode());
        drafts.update(author, draft.id(), 0, metadata("Edited"));
        long disabled = categories.create(admin, "停用分类", 0).id();
        categories.update(admin, disabled, 0, "停用分类", 0, false);
        assertEquals(ErrorCode.CATEGORY_UNAVAILABLE, assertThrows(BusinessException.class,
                () -> drafts.update(author, draft.id(), 1, new VideoMetadata("Moved", "", disabled))).getCode());
        assertEquals(ErrorCode.CATEGORY_UNAVAILABLE, assertThrows(BusinessException.class,
                () -> drafts.create(author, UUID.randomUUID().toString(), new VideoMetadata("Missing", "", Long.MAX_VALUE))).getCode());
        assertEquals(ErrorCode.CATEGORY_IN_USE, assertThrows(BusinessException.class,
                () -> categories.delete(admin, categoryId, 1)).getCode());
        assertEquals("Edited", drafts.get(author, draft.id()).metadata().title());
    }

    @Test
    void draftEndpointsExcludeBlockedDeletedAndNonDraftRecords() {
        for (String change : List.of("moderation_blocked = TRUE", "deleted_at = CURRENT_TIMESTAMP",
                "processing_status = 'PROCESSING'", "review_status = 'PENDING'", "publication_status = 'OFFLINE'")) {
            String key = UUID.randomUUID().toString();
            var draft = drafts.create(author, key, metadata("Excluded"));
            database.update("UPDATE video SET " + change + " WHERE id = ?", draft.id());
            assertEquals(ErrorCode.NOT_FOUND, assertThrows(BusinessException.class,
                    () -> drafts.get(author, draft.id())).getCode());
            assertEquals(ErrorCode.NOT_FOUND, assertThrows(BusinessException.class,
                    () -> drafts.update(author, draft.id(), 0, metadata("Forbidden"))).getCode());
            assertEquals(ErrorCode.NOT_FOUND, assertThrows(BusinessException.class,
                    () -> drafts.create(author, key, metadata("Excluded"))).getCode());
        }
        assertEquals(0L, drafts.list(author, new PageRequest(1, 20)).total());
        assertEquals(0L, database.queryForObject("SELECT COUNT(*) FROM operation_log WHERE action = 'DRAFT_UPDATED'", Long.class));
    }

    @Test
    void failedAuditRollsBackDraftAndAuditAndRequestKeyCanBeRetried() {
        String key = UUID.randomUUID().toString();
        audit.fail = true;
        assertThrows(DataIntegrityViolationException.class, () -> drafts.create(author, key, metadata("Original")));
        assertEquals(0L, database.queryForObject("SELECT COUNT(*) FROM video", Long.class));
        assertEquals(0L, database.queryForObject("SELECT COUNT(*) FROM operation_log WHERE action = 'DRAFT_CREATED'", Long.class));
        audit.fail = false;
        var draft = drafts.create(author, key, metadata("Original"));
        audit.fail = true;
        try {
            assertThrows(DataIntegrityViolationException.class, () -> drafts.update(author, draft.id(), 0, metadata("Rollback")));
        } finally {
            audit.fail = false;
        }
        assertEquals("Original", drafts.get(author, draft.id()).metadata().title());
        assertEquals(0L, drafts.get(author, draft.id()).version());
        assertEquals(0L, database.queryForObject("SELECT COUNT(*) FROM operation_log WHERE action = 'DRAFT_UPDATED'", Long.class));
    }

    @Test
    void boundedPaginationAndInputValidationApplyWithoutHttpToo() throws Exception {
        var first = create("First");
        var second = create("Second");
        assertEquals(second.id(), drafts.list(author, new PageRequest(1, 1)).items().getFirst().id());
        assertEquals(first.id(), drafts.list(author, new PageRequest(2, 1)).items().getFirst().id());
        var session = login(author);
        mvc.perform(get("/api/creator/drafts").session(session).param("size", "51")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/creator/drafts").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(createBody("bad-key", "title"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/creator/drafts").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(createBody(UUID.randomUUID().toString(), "title").replace("\"title\":", "\"ownerId\":1,\"title\":")))
                .andExpect(status().isBadRequest());
        assertEquals(ErrorCode.INVALID_REQUEST_KEY, assertThrows(BusinessException.class,
                () -> drafts.create(author, null, metadata("title"))).getCode());
        for (String title : List.of(" ", "x".repeat(121), "line\nbreak", "bad\uD800")) {
            assertEquals(ErrorCode.INVALID_VIDEO, assertThrows(BusinessException.class,
                    () -> new VideoMetadata(title, "", categoryId)).getCode());
        }
        assertThrows(BusinessException.class, () -> new VideoMetadata("title", "x".repeat(2001), categoryId));
        assertThrows(BusinessException.class, () -> new VideoMetadata("title", "bad\u0000", categoryId));
    }

    private VideoMetadata metadata(String title) { return new VideoMetadata(title, "简介", categoryId); }
    private VideoDraft create(String title) { return drafts.create(author, UUID.randomUUID().toString(), metadata(title)); }
    private Actor actor(String username) {
        var account = accounts.findSummary(username).orElseThrow();
        return new Actor(account.id(), account.username(), account.role());
    }
    private String tryCreate(String key, String title) {
        try { drafts.create(author, key, metadata(title)); return "OK"; }
        catch (BusinessException exception) { return exception.getCode().name(); }
    }
    private String tryUpdate(VideoDraft draft, String title) {
        try { drafts.update(author, draft.id(), 0, metadata(title)); return "OK"; }
        catch (BusinessException exception) { return exception.getCode().name(); }
    }
    private <Result> List<Result> race(Callable<Result> first, Callable<Result> second) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = List.of(first, second).stream().map(task -> executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return task.call();
            })).toList();
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            return List.of(futures.get(0).get(10, TimeUnit.SECONDS), futures.get(1).get(10, TimeUnit.SECONDS));
        }
    }
    private String createBody(String key, String title) throws Exception {
        return json.writeValueAsString(Map.of("requestKey", key, "title", title, "description", "简介", "categoryId", categoryId));
    }
    private String updateBody(String title, long selectedCategory, long version) throws Exception {
        return json.writeValueAsString(Map.of("title", title, "description", "简介", "categoryId", selectedCategory, "version", version));
    }
    private MockHttpSession login(Actor actor) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf()).param("username", actor.username())
                .param("password", "SafePassword123")).andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    @TestConfiguration
    static class AuditFailureConfiguration {
        @Bean @Primary
        FailingAudit failingAudit(MyBatisAuditLog delegate) { return new FailingAudit(delegate); }
    }

    static class FailingAudit implements AuditLog {
        private final AuditLog delegate;
        volatile boolean fail;
        FailingAudit(AuditLog delegate) { this.delegate = delegate; }
        @Override
        public void record(Actor actor, String action, String targetType, long targetId, String reason) {
            delegate.record(actor, action, targetType, targetId, reason);
            if (fail) throw new DataIntegrityViolationException("Injected audit failure");
        }
    }
}
