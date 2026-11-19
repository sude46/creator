package com.creator.video;

import com.creator.audit.application.AuditLog;
import com.creator.audit.infrastructure.MyBatisAuditLog;
import com.creator.catalog.application.CategoryService;
import com.creator.common.*;
import com.creator.identity.application.*;
import com.creator.identity.domain.*;
import com.creator.video.application.*;
import com.creator.video.application.processing.*;
import com.creator.video.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
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

@SpringBootTest(properties = "creator.upload.max-pending=1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(CreatorVideoIntegrationTest.Fixtures.class)
class CreatorVideoIntegrationTest {
    @Autowired JdbcTemplate database;
    @Autowired CreatorVideoService videos;
    @Autowired DraftService drafts;
    @Autowired CategoryService categories;
    @Autowired RegisterUser registration;
    @Autowired BootstrapAdmin bootstrap;
    @Autowired AccountRepository accounts;
    @Autowired ExecutionTransactions executions;
    @Autowired MockMvc mvc;
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
        database.update("UPDATE video SET source_asset_id=NULL");
        database.update("DELETE FROM media_asset");
        database.update("DELETE FROM video_favorite");
        database.update("DELETE FROM video_comment");
        database.update("DELETE FROM video");
        database.update("DELETE FROM video_category");
        database.update("DELETE FROM notification");
        database.update("DELETE FROM user_account");
        registration.execute("creator_author", "SafePassword123");
        registration.execute("creator_other", "SafePassword123");
        bootstrap.execute("creator_admin", "SafePassword123");
        author = actor("creator_author");
        other = actor("creator_other");
        admin = actor("creator_admin");
        categoryId = categories.create(admin, "creator", 0).id();
    }

    @Test
    void ownedListIncludesDraftsAndProcessedStatesWithoutPrivateStorageOrExecutionData() throws Exception {
        long id = failed();
        drafts.create(author, UUID.randomUUID().toString(), new VideoMetadata("draft", "", categoryId));
        var session = login(author);
        var response = mvc.perform(get("/api/creator/videos/{id}", id).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(Long.toString(id)))
                .andExpect(jsonPath("$.failureCode").value("STORAGE_UNAVAILABLE"))
                .andExpect(jsonPath("$.retryAllowed").value(true)).andReturn();
        String body = response.getResponse().getContentAsString();
        for (String forbidden : List.of("objectKey", "sourceKey", "executionToken", "sources/", "ownerId")) assertFalse(body.contains(forbidden));
        mvc.perform(get("/api/creator/videos").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2));
        assertEquals(0, videos.list(other, new PageRequest(1, 20)).total());
        for (Actor stranger : List.of(other, admin)) {
            assertEquals(ErrorCode.NOT_FOUND, assertThrows(BusinessException.class, () -> videos.get(stranger, id)).getCode());
            assertEquals(ErrorCode.NOT_FOUND, assertThrows(BusinessException.class, () -> videos.retry(stranger, id, 0)).getCode());
        }
        mvc.perform(get("/api/creator/videos").session(session).param("size", "51")).andExpect(status().isBadRequest());
    }

    @Test
    void retryCreatesNewGenerationAndTaskWithoutDuplicatingSourceAndRecordsAudit() {
        long id = failed();
        var result = videos.retry(author, id, 0);
        assertEquals(2, result.processingGeneration());
        assertEquals(1, result.version());
        assertEquals("PENDING", result.processingStatus());
        assertEquals("PENDING", result.taskState());
        assertEquals(0, result.executionAttempts());
        assertNull(result.failureCode());
        assertFalse(result.retryAllowed());
        assertEquals(1, database.queryForObject("SELECT COUNT(*) FROM media_asset WHERE video_id=?", Integer.class, id));
        assertEquals(2, database.queryForObject("SELECT COUNT(*) FROM processing_task WHERE video_id=?", Integer.class, id));
        assertEquals(1, database.queryForObject("SELECT COUNT(*) FROM operation_log WHERE action='PROCESSING_RETRIED' AND target_id=?", Integer.class, id));
        assertEquals(ErrorCode.STALE_WRITE, assertThrows(BusinessException.class, () -> videos.retry(author, id, 0)).getCode());
    }

    @Test
    void retryHttpRequiresAuthenticationCsrfExplicitVersionAndRejectsExtraFields() throws Exception {
        long id = failed();
        mvc.perform(get("/api/creator/videos/{id}", id)).andExpect(status().isUnauthorized());
        var session = login(author);
        mvc.perform(post("/api/creator/videos/{id}/processing-retry", id).session(session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}")).andExpect(status().isForbidden());
        for (String invalid : List.of("{}", "{\"version\":-1}", "{\"version\":0,\"ownerId\":1}")) {
            mvc.perform(post("/api/creator/videos/{id}/processing-retry", id).session(session).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/creator/videos/{id}/processing-retry", id).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.processingGeneration").value(2));
    }

    @Test
    void permanentFailureActiveTaskBlockedAndDeletedVideosCannotRetry() {
        long id = failed();
        database.update("UPDATE processing_task SET failure_code='UNSUPPORTED_MEDIA' WHERE video_id=?", id);
        assertRetryDenied(id);
        database.update("UPDATE processing_task SET failure_code='STORAGE_UNAVAILABLE', state='RUNNING' WHERE video_id=?", id);
        assertRetryDenied(id);
        database.update("UPDATE processing_task SET state='FAILED' WHERE video_id=?", id);
        database.update("UPDATE video SET moderation_blocked=TRUE WHERE id=?", id);
        assertRetryDenied(id);
        database.update("UPDATE video SET moderation_blocked=FALSE, deleted_at=CURRENT_TIMESTAMP(6) WHERE id=?", id);
        assertEquals(ErrorCode.NOT_FOUND, assertThrows(BusinessException.class, () -> videos.retry(author, id, 0)).getCode());
    }

    @Test
    void twoConcurrentRequestsOnlyCreateOneGeneration() throws Exception {
        long id = failed();
        var outcomes = race(() -> videos.retry(author, id, 0), () -> videos.retry(author, id, 0));
        assertEquals(1, outcomes.stream().filter(CreatorVideo.class::isInstance).count());
        assertEquals(1, outcomes.stream().filter(value -> value == ErrorCode.STALE_WRITE).count());
        assertEquals(2, database.queryForObject("SELECT processing_generation FROM video WHERE id=?", Integer.class, id));
    }

    @Test
    void differentVideosSharePendingQuotaAcrossIndependentConnections() throws Exception {
        long first = failed();
        long second = failed();
        var outcomes = race(() -> videos.retry(author, first, 0), () -> videos.retry(author, second, 0));
        assertEquals(1, outcomes.stream().filter(CreatorVideo.class::isInstance).count());
        assertEquals(1, outcomes.stream().filter(value -> value == ErrorCode.UPLOAD_QUOTA_EXCEEDED).count());
        assertEquals(1, database.queryForObject("SELECT COUNT(*) FROM video WHERE processing_status='PENDING' AND source_asset_id IS NOT NULL", Integer.class));
    }

    @Test
    void auditFailureRollsBackGenerationTaskAndVersion() {
        long id = failed();
        audit.fail = true;
        assertThrows(DataIntegrityViolationException.class, () -> videos.retry(author, id, 0));
        assertEquals(1, videos.get(author, id).processingGeneration());
        assertEquals(0, videos.get(author, id).version());
        assertEquals("FAILED", videos.get(author, id).processingStatus());
        assertEquals(1, database.queryForObject("SELECT COUNT(*) FROM processing_task WHERE video_id=?", Integer.class, id));
        assertEquals(0, database.queryForObject("SELECT COUNT(*) FROM operation_log WHERE action='PROCESSING_RETRIED'", Integer.class));
    }

    @Test
    void oldMessageAndExecutionCannotAffectNewGeneration() {
        long id = failed();
        long oldTask = database.queryForObject("SELECT id FROM processing_task WHERE video_id=?", Long.class, id);
        database.update("UPDATE video SET processing_status='PENDING' WHERE id=?", id);
        database.update("UPDATE processing_task SET state='PENDING',execution_attempts=0 WHERE id=?", oldTask);
        var message = new ProcessingMessage(1, oldTask, id, 1);
        var old = executions.claim(message).orElseThrow();
        database.update("UPDATE video SET processing_status='FAILED' WHERE id=?", id);
        database.update("UPDATE processing_task SET state='FAILED',failure_code='LEASE_EXPIRED' WHERE id=?", oldTask);
        var result = videos.retry(author, id, videos.get(author, id).version());
        assertFalse(executions.succeed(old, new MediaMetadata(1000, 320, 240), 10));
        assertTrue(executions.claim(message).isEmpty());
        assertEquals(result, videos.get(author, id));
    }

    @Test
    void inactiveOrRoleChangedActorCannotRetryEvenThroughInternalUseCase() {
        long id = failed();
        database.update("UPDATE user_account SET status='DISABLED' WHERE id=?", author.id());
        assertEquals(ErrorCode.SESSION_INVALID, assertThrows(BusinessException.class, () -> videos.retry(author, id, 0)).getCode());
        database.update("UPDATE user_account SET status='ACTIVE', role='ADMIN' WHERE id=?", author.id());
        assertEquals(ErrorCode.SESSION_INVALID, assertThrows(BusinessException.class, () -> videos.retry(author, id, 0)).getCode());
    }

    private void assertRetryDenied(long id) {
        assertEquals(ErrorCode.PROCESSING_RETRY_NOT_ALLOWED, assertThrows(BusinessException.class, () -> videos.retry(author, id, 0)).getCode());
    }
    private long failed() {
        long id = drafts.create(author, UUID.randomUUID().toString(), new VideoMetadata("failed", "", categoryId)).id();
        database.update("INSERT INTO media_asset (video_id,kind,object_key,size_bytes) VALUES (?,'SOURCE',?,64)", id, "sources/" + UUID.randomUUID() + ".mp4");
        long asset = database.queryForObject("SELECT id FROM media_asset WHERE video_id=?", Long.class, id);
        database.update("UPDATE video SET source_asset_id=?, processing_status='FAILED' WHERE id=?", asset, id);
        database.update("INSERT INTO processing_task (video_id,state,failure_code,execution_attempts) VALUES (?,'FAILED','STORAGE_UNAVAILABLE',3)", id);
        return id;
    }
    private Actor actor(String name) { var account = accounts.findSummary(name).orElseThrow(); return new Actor(account.id(), name, account.role()); }
    private MockHttpSession login(Actor actor) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf()).param("username", actor.username()).param("password", "SafePassword123"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    private List<Object> race(Callable<Object> first, Callable<Object> second) throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstResult = executor.submit(() -> attempt(start, first));
            var secondResult = executor.submit(() -> attempt(start, second));
            start.countDown();
            return List.of(firstResult.get(10, TimeUnit.SECONDS), secondResult.get(10, TimeUnit.SECONDS));
        }
    }
    private Object attempt(CountDownLatch start, Callable<Object> operation) throws Exception {
        start.await();
        try { return operation.call(); } catch (BusinessException exception) { return exception.getCode(); }
    }
    static class FailingAudit implements AuditLog {
        private final MyBatisAuditLog delegate;
        boolean fail;
        FailingAudit(MyBatisAuditLog delegate) { this.delegate = delegate; }
        public void record(Actor actor, String action, String targetType, long targetId, String reason) {
            delegate.record(actor, action, targetType, targetId, reason);
            if (fail) throw new DataIntegrityViolationException("injected audit failure");
        }
    }
    @TestConfiguration
    static class Fixtures {
        @Bean @Primary FailingAudit failingAudit(MyBatisAuditLog delegate) { return new FailingAudit(delegate); }
        @Bean ExecutionTransactions executionTransactions(ExecutionRepository repository, Clock clock) {
            return new ExecutionTransactions(repository, new WorkerProperties("unused", "unused", "unused", 3, 90, 150, 1, 1, 1048576, 1048576), clock);
        }
    }
}
