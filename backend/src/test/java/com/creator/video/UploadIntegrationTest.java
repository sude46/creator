package com.creator.video;

import com.creator.audit.application.AuditLog;
import com.creator.audit.infrastructure.MyBatisAuditLog;
import com.creator.catalog.application.CategoryService;
import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.identity.application.BootstrapAdmin;
import com.creator.identity.application.RegisterUser;
import com.creator.identity.domain.AccountRepository;
import com.creator.identity.domain.Actor;
import com.creator.video.application.*;
import com.creator.video.application.processing.DispatchService;
import com.creator.video.application.processing.TaskPublisher;
import com.creator.video.application.deletion.VideoDeletionMaintenance;
import com.creator.video.domain.*;
import com.creator.video.infrastructure.upload.UploadMaintenance;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.address=127.0.0.1", "creator.upload.enabled=true", "creator.storage.provider=test",
        "creator.upload.max-bytes=1024", "creator.upload.min-free-bytes=0", "creator.upload.user-quota-bytes=2048",
        "creator.upload.max-pending=2", "creator.upload.temp-directory=../.local/upload-test-temp",
        "creator.processing.dispatch.enabled=true", "creator.processing.dispatch.provider=test",
        "creator.processing.dispatch.scan-milliseconds=3600000",
        "creator.upload.cleanup-interval-ms=3600000"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(UploadIntegrationTest.StorageConfiguration.class)
class UploadIntegrationTest {
    @LocalServerPort int port;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate database;
    @Autowired DraftService drafts;
    @Autowired CategoryService categories;
    @Autowired RegisterUser registerUser;
    @Autowired BootstrapAdmin bootstrapAdmin;
    @Autowired AccountRepository accounts;
    @Autowired UploadService uploads;
    @Autowired UploadTransactions transactions;
    @Autowired UploadRepository repository;
    @Autowired UploadAdmission admission;
    @Autowired UploadProperties properties;
    @Autowired UploadStaging staging;
    @Autowired UploadMaintenance maintenance;
    @Autowired com.creator.video.application.VideoDeletionService deletionService;
    @Autowired com.creator.video.application.VideoProcessingService processingService;
    @Autowired com.creator.video.application.CreatorVideoService creatorVideos;
    @Autowired com.creator.video.application.PublicationService publications;
    @Autowired VideoDeletionMaintenance deletionMaintenance;
    @Autowired MemoryStorage storage;
    @Autowired AuditLog audit;
    @Autowired FailingAudit failingAudit;
    @Autowired Clock clock;
    @Autowired ObjectMapper json;
    @Autowired DispatchService dispatcher;
    @Autowired RecordingTaskPublisher taskPublisher;
    Actor author;
    Actor other;
    long categoryId;

    @BeforeEach
    void reset() throws Exception {
        storage.objects.clear();
        taskPublisher.sent.clear();
        failingAudit.fail = false;
        storage.failWrite = false;
        storage.failDelete = false;
        storage.afterPut = () -> { };
        database.update("DELETE FROM operation_log");
        database.update("DELETE FROM review_record");
        database.update("DELETE FROM processing_task");
        database.update("DELETE FROM video_deletion");
        database.update("DELETE FROM upload_attempt");
        database.update("UPDATE video SET source_asset_id = NULL");
        database.update("DELETE FROM media_asset");
        database.update("DELETE FROM video_favorite");
        database.update("DELETE FROM video_comment_like");
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
        database.update("DELETE FROM user_profile");
        database.update("DELETE FROM user_follow");
        database.update("DELETE FROM video_collection_item");
        database.update("DELETE FROM video_collection");
        database.update("DELETE FROM user_account");
        registerUser.execute("upload_author", "SafePassword123");
        registerUser.execute("upload_other", "SafePassword123");
        bootstrapAdmin.execute("upload_admin", "SafePassword123");
        author = actor("upload_author");
        other = actor("upload_other");
        categoryId = categories.create(actor("upload_admin"), "视频", 0).id();
        assertTempEmpty();
    }

    @Test
    void uploadTaskOnlyBecomesDispatchableAfterBindingCommits() {
        long id = draft(author);
        storage.afterPut = () -> {
            dispatcher.run();
            assertTrue(taskPublisher.sent.isEmpty());
        };
        uploads.upload(author, id, 0, new ByteArrayInputStream(mp4(1024)), 1024);
        assertTrue(taskPublisher.sent.isEmpty());
        dispatcher.run();
        assertEquals(1, taskPublisher.sent.size());
        assertEquals(id, taskPublisher.sent.getFirst().videoId());
        assertNotNull(database.queryForObject("SELECT published_at FROM processing_task WHERE video_id = ?", java.time.LocalDateTime.class, id));
    }

    @Test
    void creatorExportEscapesCellsAndAdminCanListProcessing() throws Exception {
        long id = draft(author);
        uploads.upload(author, id, 0, new ByteArrayInputStream(mp4(1024)), 1024);
        database.update("UPDATE video SET title = ? WHERE id = ?", "=SUM(1)", id);
        var csv = new String(creatorVideos.exportCsv(author), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("id,title,categoryId"));
        assertTrue(csv.contains("\"'=SUM(1)\""));
        mvc.perform(get("/api/creator/videos/export").session(login(author)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"videos.csv\""))
                .andExpect(content().contentTypeCompatibleWith("text/csv"));

        var admin = actor("upload_admin");
        assertEquals(1, publications.processing(admin, new com.creator.common.PageRequest(1, 20)).total());
        assertThrows(com.creator.common.BusinessException.class,
                () -> publications.processing(author, new com.creator.common.PageRequest(1, 20)));
        mvc.perform(get("/api/admin/videos/processing").session(login(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].video.id").value(Long.toString(id)));
        mvc.perform(get("/api/admin/videos/processing").session(login(other)))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerCanCancelProcessingAndRetryCancelledGeneration() {
        long id = draft(author);
        uploads.upload(author, id, 0, new ByteArrayInputStream(mp4(1024)), 1024);
        var cancelled = processingService.cancelByOwner(author, id, 1);
        assertEquals("FAILED", cancelled.processingStatus());
        assertEquals(com.creator.video.domain.WorkStatus.FAILED, cancelled.workStatus());
        assertEquals("视频处理失败", cancelled.statusReason());
        assertEquals("CANCELLED", database.queryForObject(
                "SELECT state FROM processing_task WHERE video_id=? AND generation=1", String.class, id));
        assertTrue(cancelled.retryAllowed());
        var retried = creatorVideos.retry(author, id, cancelled.version());
        assertEquals("PENDING", retried.processingStatus());
        assertEquals(com.creator.video.domain.WorkStatus.PROCESSING, retried.workStatus());
        assertEquals(2, database.queryForObject(
                "SELECT generation FROM processing_task WHERE video_id=? ORDER BY generation DESC LIMIT 1", Long.class, id));
        var admin = actor("upload_admin");
        long second = draft(author);
        assertThrows(com.creator.common.BusinessException.class,
                () -> processingService.cancelByAdmin(author, second, 0, new ModerationReason("no")));
        assertNotNull(processingService.cancelByAdmin(admin, second, 0, new ModerationReason("ops")));
    }

    @Test
    void adminDeletionRequiresAdminAndStoresReason() {
        long id = draft(author);
        var admin = actor("upload_admin");
        assertThrows(com.creator.common.BusinessException.class,
                () -> deletionService.deleteByAdmin(author, id, 0, new ModerationReason("not admin")));
        deletionService.deleteByAdmin(admin, id, 0, new ModerationReason("policy removal"));
        assertEquals("policy removal", database.queryForObject(
                "SELECT reason FROM video_deletion WHERE video_id=?", String.class, id));
        assertEquals(1, database.queryForObject(
                "SELECT version FROM video WHERE id=?", Long.class, id));
    }

    @Test
    void ownerDeletionCancelsTasksAndReclaimsObjectsAndAssets() {
        long id = draft(author);
        uploads.upload(author, id, 0, new ByteArrayInputStream(mp4(1024)), 1024);
        assertFalse(storage.objects.isEmpty());
        deletionService.deleteByOwner(author, id, 1);
        assertNotNull(database.queryForObject("SELECT deleted_at FROM video WHERE id=?", java.time.LocalDateTime.class, id));
        assertEquals("CANCELLED", database.queryForObject("SELECT state FROM processing_task WHERE video_id=?", String.class, id));
        assertEquals(1, database.queryForObject(
                "SELECT COUNT(*) FROM operation_log WHERE action='VIDEO_DELETED' AND target_type='VIDEO' AND target_id=?", Integer.class, id));
        assertEquals("PENDING", database.queryForObject("SELECT state FROM video_deletion WHERE video_id=?", String.class, id));

        deletionMaintenance.clean();

        assertTrue(storage.objects.isEmpty());
        assertEquals("DONE", database.queryForObject("SELECT state FROM video_deletion WHERE video_id=?", String.class, id));
        assertNull(database.queryForObject("SELECT source_asset_id FROM video WHERE id=?", Long.class, id));
        assertEquals(0, database.queryForObject("SELECT COUNT(*) FROM media_asset WHERE video_id=?", Integer.class, id));
    }

    @Test
    void otherUserCannotDeleteOwnedVideoAndStorageFailureRetries() {
        long id = draft(author);
        uploads.upload(author, id, 0, new ByteArrayInputStream(mp4(1024)), 1024);
        assertThrows(com.creator.common.BusinessException.class, () -> deletionService.deleteByOwner(other, id, 1));
        deletionService.deleteByOwner(author, id, 1);
        storage.failDelete = true;
        deletionMaintenance.clean();
        assertEquals("PENDING", database.queryForObject("SELECT state FROM video_deletion WHERE video_id=?", String.class, id));
        storage.failDelete = false;
    }

    @Test
    void realEmbeddedTomcatAcceptsAuthenticatedRawFileUpload() throws Exception {
        long id = draft(author);
        String base = "http://127.0.0.1:" + port;
        try (var client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(5)).build()) {
            var csrfResponse = client.send(HttpRequest.newBuilder(URI.create(base + "/api/auth/csrf"))
                    .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
            var token = json.readTree(csrfResponse.body());
            var loginResponse = client.send(HttpRequest.newBuilder(URI.create(base + "/api/auth/login"))
                    .timeout(Duration.ofSeconds(10)).header(token.get("headerName").asText(), token.get("token").asText())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("username=upload_author&password=SafePassword123")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, loginResponse.statusCode());
            csrfResponse = client.send(HttpRequest.newBuilder(URI.create(base + "/api/auth/csrf"))
                    .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
            token = json.readTree(csrfResponse.body());
            var response = client.send(HttpRequest.newBuilder(URI.create(base + "/api/creator/videos/" + id + "/source"))
                    .timeout(Duration.ofSeconds(10)).header(token.get("headerName").asText(), token.get("token").asText())
                    .header("Content-Type", "video/mp4").header("X-Video-Version", "0")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(mp4(1024))).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), response.body());
            assertTrue(json.readTree(response.body()).get("sourceUploaded").asBoolean());
        }
        assertEquals(1L, count("processing_task"));
        assertTempEmpty();
    }

    @Test
    void uploadBindsPrivateSourceAndDurableTaskTogether() throws Exception {
        long id = draft(author);
        var session = login(author);
        mvc.perform(put("/api/creator/videos/{id}/source", id).session(session).with(csrf().asHeader())
                .header("X-Video-Version", "0").contentType("video/mp4").content(mp4(1024)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.videoId").value(Long.toString(id)))
                .andExpect(jsonPath("$.sourceUploaded").value(true)).andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.processingStatus").value("PENDING"))
                .andExpect(jsonPath("$.objectKey").doesNotExist());
        mvc.perform(get("/api/creator/videos/{id}/upload", id).session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceUploaded").value(true));
        mvc.perform(get("/api/creator/drafts/{id}", id).session(session)).andExpect(status().isNotFound());
        assertEquals(1L, count("media_asset"));
        assertEquals(1L, count("processing_task"));
        assertEquals("PENDING", database.queryForObject("SELECT state FROM processing_task", String.class));
        assertEquals("BOUND", database.queryForObject("SELECT state FROM upload_attempt", String.class));
        assertEquals(1L, database.queryForObject("SELECT COUNT(*) FROM operation_log WHERE action = 'SOURCE_UPLOADED'", Long.class));
        assertArrayEquals(mp4(1024), storage.objects.values().iterator().next());
        assertTempEmpty();
    }

    @Test
    void missingAndInvalidCsrfAndIdentityFailBeforeReadingBody() throws Exception {
        long id = draft(author);
        String path = "/api/creator/videos/" + id + "/source";
        var session = login(author);
        for (String token : new String[] {"", "invalid"}) {
            var consumed = new AtomicBoolean();
            mvc.perform(context -> {
                var request = guarded(context, path, session, consumed);
                if (!token.isEmpty()) request.addHeader("X-CSRF-TOKEN", token);
                return request;
            }).andExpect(status().isForbidden());
            assertFalse(consumed.get());
        }
        var consumed = new AtomicBoolean();
        mvc.perform(context -> guarded(context, path, null, consumed)).andExpect(status().isUnauthorized());
        assertFalse(consumed.get());
        database.update("UPDATE user_account SET status = 'DISABLED' WHERE id = ?", author.id());
        mvc.perform(context -> guarded(context, path, session, consumed)).andExpect(status().isUnauthorized());
        assertFalse(consumed.get());
        assertEquals(0L, count("upload_attempt"));
        assertTempEmpty();
    }

    @Test
    void ownershipVersionAndContentTypeFailWithoutStagingBytes() throws Exception {
        long id = draft(author);
        var session = login(other);
        var tokenResult = mvc.perform(get("/api/auth/csrf").session(session)).andReturn();
        String token = json.readTree(tokenResult.getResponse().getContentAsString()).get("token").asText();
        var consumed = new AtomicBoolean();
        mvc.perform(context -> {
            var request = guarded(context, "/api/creator/videos/" + id + "/source", session, consumed);
            request.addHeader("X-CSRF-TOKEN", token);
            return request;
        }).andExpect(status().isNotFound());
        assertFalse(consumed.get());
        mvc.perform(get("/api/creator/videos/{id}/upload", id).session(session)).andExpect(status().isNotFound());
        mvc.perform(put("/api/creator/videos/{id}/source", id).session(login(author)).with(csrf().asHeader())
                .header("X-Video-Version", "99").contentType("video/mp4").content(mp4(1024)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));
        mvc.perform(put("/api/creator/videos/{id}/source", id).session(login(author)).with(csrf().asHeader())
                .header("X-Video-Version", "0").contentType("multipart/form-data").content(mp4(1024)))
                .andExpect(status().isUnsupportedMediaType());
        assertEquals(0L, count("upload_attempt"));
        assertTempEmpty();
    }

    @Test
    void invalidOversizedAndLengthMismatchedStreamsLeaveNoBoundData() throws Exception {
        long id = draft(author);
        var session = login(author);
        mvc.perform(put("/api/creator/videos/{id}/source", id).session(session).with(csrf().asHeader())
                .header("X-Video-Version", "0").contentType("video/mp4").content(mp4(1025)))
                .andExpect(status().isPayloadTooLarge());
        assertEquals(0L, count("upload_attempt"));
        mvc.perform(put("/api/creator/videos/{id}/source", id).session(session).with(csrf().asHeader())
                .header("X-Video-Version", "0").contentType("video/mp4").content("not a video"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_MEDIA"));
        assertEquals(ErrorCode.UPLOAD_TOO_LARGE, assertThrows(BusinessException.class,
                () -> uploads.upload(author, id, 0, new ByteArrayInputStream(mp4(1025)), -1)).getCode());
        expireAttempts();
        maintenance.clean();
        assertEquals(ErrorCode.INVALID_MEDIA, assertThrows(BusinessException.class,
                () -> uploads.upload(author, id, 0, new ByteArrayInputStream(mp4(512)), 513)).getCode());
        assertEquals(0L, count("media_asset"));
        assertEquals(0L, count("processing_task"));
        assertTrue(storage.objects.isEmpty());
        assertTempEmpty();
    }

    @Test
    void admissionHasPerUserAndGlobalBoundsAndAlwaysReleases() throws Exception {
        long id = draft(author);
        var session = login(author);
        try (var authorLease = admission.acquire(author.id(), 100)) {
            mvc.perform(put("/api/creator/videos/{id}/source", id).session(session).with(csrf().asHeader())
                    .header("X-Video-Version", "0").contentType("video/mp4").content(mp4(1024)))
                    .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "60"));
            try (var secondLease = admission.acquire(other.id(), 100)) {
                assertEquals(ErrorCode.UPLOAD_BUSY, assertThrows(BusinessException.class,
                        () -> admission.acquire(Long.MAX_VALUE, 100)).getCode());
            }
        }
        mvc.perform(put("/api/creator/videos/{id}/source", id).session(session).with(csrf().asHeader())
                .header("X-Video-Version", "0").contentType("video/mp4").content("invalid"))
                .andExpect(status().isBadRequest());
        try (var restored = admission.acquire(author.id(), 100)) { assertNotNull(restored); }
        assertTempEmpty();
    }

    @Test
    void storageFailureAfterWriteLeavesRecoverableAttemptAndCleanupRetries() throws Exception {
        long id = draft(author);
        storage.failWrite = true;
        assertThrows(DataAccessResourceFailureException.class,
                () -> uploads.upload(author, id, 0, new ByteArrayInputStream(mp4(512)), 512));
        assertEquals(1, storage.objects.size());
        assertEquals(0L, count("media_asset"));
        assertEquals(0L, count("processing_task"));
        maintenance.clean();
        assertEquals(1, storage.objects.size());
        expireAttempts();
        storage.failDelete = true;
        maintenance.clean();
        assertEquals("CLEANING", database.queryForObject("SELECT state FROM upload_attempt", String.class));
        storage.failDelete = false;
        expireAttempts();
        maintenance.clean();
        maintenance.clean();
        assertTrue(storage.objects.isEmpty());
        assertEquals("DELETED", database.queryForObject("SELECT state FROM upload_attempt", String.class));
        assertTempEmpty();
    }

    @Test
    void versionChangeDuringObjectPutPreventsBindingAndLeavesOrphanForSafeCleanup() throws Exception {
        long id = draft(author);
        storage.afterPut = () -> drafts.update(author, id, 0, new VideoMetadata("Changed", "", categoryId));
        assertEquals(ErrorCode.STALE_WRITE, assertThrows(BusinessException.class,
                () -> uploads.upload(author, id, 0, new ByteArrayInputStream(mp4(512)), 512)).getCode());
        assertEquals(0L, count("media_asset"));
        assertEquals(0L, count("processing_task"));
        assertEquals(1, storage.objects.size());
        expireAttempts();
        maintenance.clean();
        assertTrue(storage.objects.isEmpty());
        assertTempEmpty();
    }

    @Test
    void commitSucceededButResponseFailedNeverDeletesBoundObject() throws Exception {
        long id = draft(author);
        var uncertain = new UploadTransactions(repository, audit, properties, clock) {
            @Override
            public UploadAttempt reserve(Actor actor, long videoId, long version) { return transactions.reserve(actor, videoId, version); }
            @Override
            public UploadVideo bind(Actor actor, UploadAttempt attempt, long size, long version) {
                transactions.bind(actor, attempt, size, version);
                throw new DataAccessResourceFailureException("Injected lost commit acknowledgement");
            }
        };
        var service = new UploadService(uncertain, staging, storage);
        assertThrows(DataAccessResourceFailureException.class,
                () -> service.upload(author, id, 0, new ByteArrayInputStream(mp4(512)), 512));
        assertTrue(transactions.status(author, id).sourceAssetId() != null);
        expireAttempts();
        maintenance.clean();
        assertEquals(1, storage.objects.size());
        assertEquals(1L, count("media_asset"));
        assertEquals(1L, count("processing_task"));
        assertEquals(ErrorCode.SOURCE_ALREADY_BOUND, assertThrows(BusinessException.class,
                () -> uploads.upload(author, id, 1, new ByteArrayInputStream(mp4(512)), 512)).getCode());
        assertEquals(1L, count("upload_attempt"));
        assertTempEmpty();
    }

    @Test
    void auditFailureRollsBackSourceTaskVersionAndBindingTogether() throws Exception {
        long id = draft(author);
        failingAudit.fail = true;
        try {
            assertThrows(DataIntegrityViolationException.class,
                    () -> uploads.upload(author, id, 0, new ByteArrayInputStream(mp4(512)), 512));
        } finally { failingAudit.fail = false; }
        assertEquals(0L, count("media_asset"));
        assertEquals(0L, count("processing_task"));
        assertNull(transactions.status(author, id).sourceAssetId());
        assertEquals(0L, transactions.status(author, id).version());
        assertEquals("UPLOADING", database.queryForObject("SELECT state FROM upload_attempt", String.class));
        assertEquals(0L, database.queryForObject("SELECT COUNT(*) FROM operation_log WHERE action = 'SOURCE_UPLOADED'", Long.class));
        expireAttempts();
        maintenance.clean();
        assertTrue(storage.objects.isEmpty());
        assertTempEmpty();
    }

    @Test
    void concurrentUploadsOnlyBindOneSourceAndCleanupKeepsTheWinner() throws Exception {
        long id = draft(author);
        var results = competingUploads(id, id);
        assertEquals(1, results.stream().filter("OK"::equals).count());
        assertEquals(1, results.stream().filter("SOURCE_ALREADY_BOUND"::equals).count());
        assertEquals(1L, count("media_asset"));
        assertEquals(1L, count("processing_task"));
        assertEquals(2, storage.objects.size());
        expireAttempts();
        maintenance.clean();
        assertEquals(1, storage.objects.size());
        assertTempEmpty();
    }

    @Test
    void concurrentBindingsCannotExceedUserQuotaAcrossInstances() throws Exception {
        long existing = draft(author);
        uploads.upload(author, existing, 0, new ByteArrayInputStream(mp4(1024)), 1024);
        database.update("UPDATE video SET processing_status = 'READY' WHERE id = ?", existing);
        var results = competingUploads(draft(author), draft(author));
        assertEquals(1, results.stream().filter("OK"::equals).count());
        assertEquals(1, results.stream().filter("UPLOAD_QUOTA_EXCEEDED"::equals).count());
        assertEquals(2048L, repository.usedBytes(author.id()));
        assertEquals(2L, count("media_asset"));
        assertEquals(2L, count("processing_task"));
        expireAttempts();
        maintenance.clean();
        assertEquals(2, storage.objects.size());
        assertTempEmpty();
    }

    @Test
    void cleanerClaimPreventsExpiredAttemptFromBinding() {
        long id = draft(author);
        var attempt = transactions.reserve(author, id, 0);
        expireAttempts();
        assertTrue(transactions.claimCleanup(attempt.id()).isPresent());
        assertEquals(ErrorCode.UPLOAD_TIMEOUT, assertThrows(BusinessException.class,
                () -> transactions.bind(author, attempt, 512, 0)).getCode());
        assertEquals(0L, count("media_asset"));
        assertEquals(0L, count("processing_task"));
    }

    @Test
    void finalQuotaCheckSerializesUserUsageAndAttemptsAreBounded() throws Exception {
        long first = draft(author);
        long second = draft(author);
        uploads.upload(author, first, 0, new ByteArrayInputStream(mp4(1024)), 1024);
        database.update("UPDATE video SET processing_status = 'READY' WHERE id = ?", first);
        uploads.upload(author, second, 0, new ByteArrayInputStream(mp4(1024)), 1024);
        long third = draft(author);
        assertEquals(ErrorCode.UPLOAD_QUOTA_EXCEEDED, assertThrows(BusinessException.class,
                () -> uploads.upload(author, third, 0, new ByteArrayInputStream(mp4(512)), 512)).getCode());
        assertEquals(2L, count("media_asset"));
        long otherDraft = draft(other);
        transactions.reserve(other, otherDraft, 0);
        transactions.reserve(other, otherDraft, 0);
        assertEquals(ErrorCode.UPLOAD_BUSY, assertThrows(BusinessException.class,
                () -> transactions.reserve(other, otherDraft, 0)).getCode());
        assertTempEmpty();
    }

    private long draft(Actor owner) { return drafts.create(owner, UUID.randomUUID().toString(), new VideoMetadata("作品", "", categoryId)).id(); }
    private List<String> competingUploads(long first, long second) throws Exception {
        var arrived = new CountDownLatch(2);
        storage.afterPut = () -> {
            arrived.countDown();
            try { assertTrue(arrived.await(5, TimeUnit.SECONDS)); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var calls = List.of(first, second).stream().map(id -> (Callable<String>) () -> {
                try {
                    uploads.upload(author, id, 0, new ByteArrayInputStream(mp4(1024)), 1024);
                    return "OK";
                } catch (BusinessException exception) { return exception.getCode().name(); }
            }).map(executor::submit).toList();
            return List.of(calls.get(0).get(10, TimeUnit.SECONDS), calls.get(1).get(10, TimeUnit.SECONDS));
        } finally { storage.afterPut = () -> { }; }
    }
    private long count(String table) { return database.queryForObject("SELECT COUNT(*) FROM " + table, Long.class); }
    private void expireAttempts() { database.update("UPDATE upload_attempt SET expires_at = TIMESTAMP '2000-01-01 00:00:00'"); }
    private void assertTempEmpty() throws IOException {
        try (var paths = Files.list(Path.of(properties.tempDirectory()))) { assertEquals(0, paths.count()); }
    }
    private Actor actor(String name) {
        var account = accounts.findSummary(name).orElseThrow();
        return new Actor(account.id(), account.username(), account.role());
    }
    private MockHttpSession login(Actor actor) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf()).param("username", actor.username())
                .param("password", "SafePassword123")).andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    static byte[] mp4(int size) {
        byte[] bytes = new byte[size];
        ByteBuffer.wrap(bytes).putInt(24).put("ftypisom".getBytes(StandardCharsets.US_ASCII));
        return bytes;
    }
    private MockHttpServletRequest guarded(jakarta.servlet.ServletContext context, String path,
            MockHttpSession session, AtomicBoolean consumed) {
        var request = new MockHttpServletRequest(context, "PUT", path) {
            @Override public ServletInputStream getInputStream() {
                var delegate = super.getInputStream();
                return new ServletInputStream() {
                    @Override public int read() throws IOException { consumed.set(true); return delegate.read(); }
                    @Override public boolean isFinished() { return delegate.isFinished(); }
                    @Override public boolean isReady() { return delegate.isReady(); }
                    @Override public void setReadListener(ReadListener listener) { delegate.setReadListener(listener); }
                };
            }
        };
        if (session != null) request.setSession(session);
        request.setContentType("video/mp4");
        request.setContent(mp4(1024));
        request.addHeader("X-Video-Version", "0");
        return request;
    }

    @TestConfiguration
    static class StorageConfiguration {
        @Bean MemoryStorage memoryStorage() { return new MemoryStorage(); }
        @Bean RecordingTaskPublisher recordingTaskPublisher() { return new RecordingTaskPublisher(); }
        @Bean @Primary FailingAudit failingAudit(MyBatisAuditLog delegate) { return new FailingAudit(delegate); }
    }
    static class RecordingTaskPublisher implements TaskPublisher {
        final List<DispatchClaim> sent = new java.util.concurrent.CopyOnWriteArrayList<>();
        @Override public PublicationResult publish(DispatchClaim claim) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            sent.add(claim);
            return PublicationResult.CONFIRMED;
        }
    }
    static class FailingAudit implements AuditLog {
        private final AuditLog delegate;
        volatile boolean fail;
        FailingAudit(AuditLog delegate) { this.delegate = delegate; }
        @Override public void record(Actor actor, String action, String targetType, long targetId, String reason) {
            delegate.record(actor, action, targetType, targetId, reason);
            if (fail) throw new DataIntegrityViolationException("Injected audit failure");
        }
    }
    static class MemoryStorage implements ObjectStorage {
        final Map<String, byte[]> objects = new ConcurrentHashMap<>();
        volatile boolean failWrite;
        volatile boolean failDelete;
        volatile Runnable afterPut = () -> { };
        @Override public void verifyPrivateBucket() { }
        @Override public void put(String key, Path file, long size) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            try { objects.put(key, Files.readAllBytes(file)); }
            catch (IOException exception) { throw new IllegalStateException(exception); }
            afterPut.run();
            if (failWrite) throw new DataAccessResourceFailureException("Injected storage failure after write");
        }
        @Override public void delete(String key) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            if (failDelete) throw new DataAccessResourceFailureException("Injected storage delete failure");
            objects.remove(key);
        }
    }
}
