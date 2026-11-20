package com.creator.video;

import com.creator.video.application.processing.*;
import com.creator.video.domain.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(ExecutionIntegrationTest.Fixtures.class)
class ExecutionIntegrationTest {
    @Autowired JdbcTemplate database;
    @Autowired ExecutionTransactions transactions;
    @Autowired ExecutionRepository repository;
    @Autowired ProcessingWorker worker;
    @Autowired ExecutionMaintenance maintenance;
    @Autowired FakeStorage storage;
    @Autowired FakeMedia media;
    @Autowired TestClock clock;
    @Autowired WorkerProperties properties;
    long videoId;
    ProcessingMessage message;

    @BeforeEach
    void reset() {
        clock.instant.set(Instant.now());
        storage.objects.clear();
        storage.failDelete = false;
        storage.afterPut = () -> { };
        storage.beforeDownload = () -> { };
        media.failure = null;
        database.update("DELETE FROM operation_log");
        database.update("DELETE FROM review_record");
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
        database.update("DELETE FROM user_profile");
        database.update("DELETE FROM user_account");
        database.update("INSERT INTO user_account (username, password_hash) VALUES ('worker_author', 'not-a-password')");
        long owner = database.queryForObject("SELECT id FROM user_account WHERE username='worker_author'", Long.class);
        database.update("INSERT INTO video_category (name, name_key) VALUES ('worker', ?)", new byte[]{1});
        long category = database.queryForObject("SELECT id FROM video_category WHERE name='worker'", Long.class);
        database.update("INSERT INTO video (owner_id, category_id, title, description, client_request_id, creation_fingerprint) VALUES (?, ?, 'worker', '', ?, ?)",
                owner, category, UUID.randomUUID().toString(), new byte[32]);
        videoId = database.queryForObject("SELECT id FROM video WHERE owner_id=?", Long.class, owner);
        database.update("INSERT INTO media_asset (video_id, kind, object_key, size_bytes) VALUES (?, 'SOURCE', 'sources/test.mp4', 64)", videoId);
        long source = database.queryForObject("SELECT id FROM media_asset WHERE video_id=?", Long.class, videoId);
        database.update("UPDATE video SET source_asset_id=? WHERE id=?", source, videoId);
        database.update("INSERT INTO processing_task (video_id) VALUES (?)", videoId);
        long taskId = database.queryForObject("SELECT id FROM processing_task WHERE video_id=?", Long.class, videoId);
        message = new ProcessingMessage(1, taskId, videoId, 1);
        clock.advance(1);
    }

    @Test
    void successfulProcessingBindsMetadataAndOneCoverWithoutPublishingAndDuplicateIsSafe() {
        assertEquals(ProcessingWorker.Decision.ACK, worker.consume(message));
        assertEquals("SUCCEEDED", taskState());
        assertEquals("READY", videoState());
        assertEquals(1234L, database.queryForObject("SELECT duration_millis FROM video WHERE id=?", Long.class, videoId));
        assertEquals("UNPUBLISHED", database.queryForObject("SELECT publication_status FROM video WHERE id=?", String.class, videoId));
        assertEquals("NOT_SUBMITTED", database.queryForObject("SELECT review_status FROM video WHERE id=?", String.class, videoId));
        worker.consume(message);
        assertEquals(1, storage.objects.size());
        assertEquals(1, database.queryForObject("SELECT execution_attempts FROM processing_task WHERE id=?", Integer.class, message.taskId()));
        assertEquals(1, database.queryForObject("SELECT COUNT(*) FROM media_asset WHERE kind='COVER'", Integer.class));
    }

    @Test
    void competingConnectionsOnlyOneCanClaimExecution() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return transactions.claim(message); });
            var second = executor.submit(() -> { start.await(); return transactions.claim(message); });
            start.countDown();
            assertNotEquals(first.get(10, TimeUnit.SECONDS).isPresent(), second.get(10, TimeUnit.SECONDS).isPresent());
        }
        assertEquals(ProcessingWorker.Decision.ACK, worker.consume(message));
        assertTrue(storage.objects.isEmpty());
    }

    @Test
    void expiredExecutionCannotCommitAndRecoveryAllowsNewToken() {
        var old = transactions.claim(message).orElseThrow();
        clock.advance(properties.leaseSeconds() + 1);
        assertFalse(transactions.succeed(old, new MediaMetadata(1000, 640, 360), 10));
        maintenance.run();
        assertEquals("RETRY_WAIT", taskState());
        clock.advance(200);
        maintenance.run();
        assertEquals("PENDING", taskState());
        assertEquals("PENDING", videoState());
        var current = transactions.claim(message).orElseThrow();
        assertNotEquals(old.executionToken(), current.executionToken());
        assertFalse(transactions.succeed(old, new MediaMetadata(1000, 640, 360), 10));
        assertTrue(transactions.succeed(current, new MediaMetadata(1000, 640, 360), 10));
    }

    @Test
    void transientFailuresHaveBackoffAndThreeTotalAttempts() {
        media.failure = ProcessingFailure.PROCESS_TIMEOUT;
        for (int attempt = 1; attempt <= 3; attempt++) {
            worker.consume(message);
            assertEquals(attempt == 3 ? "FAILED" : "RETRY_WAIT", taskState());
            worker.consume(message);
            assertEquals(attempt, database.queryForObject("SELECT execution_attempts FROM processing_task WHERE id=?", Integer.class, message.taskId()));
            clock.advance(200);
            maintenance.run();
        }
        assertEquals("FAILED", videoState());
        assertEquals("PROCESS_TIMEOUT", database.queryForObject("SELECT failure_code FROM processing_task WHERE id=?", String.class, message.taskId()));
    }

    @Test
    void invalidMediaFailsPermanentlyWithoutBindingCover() {
        media.failure = ProcessingFailure.UNSUPPORTED_MEDIA;
        worker.consume(message);
        assertEquals("FAILED", taskState());
        clock.advance(1000);
        maintenance.run();
        worker.consume(message);
        assertTrue(storage.objects.isEmpty());
        assertEquals(0, database.queryForObject("SELECT COUNT(*) FROM media_asset WHERE kind='COVER'", Integer.class));
    }

    @Test
    void videoDeletionDuringStoragePreventsLateBindingAndOrphanIsCleaned() {
        storage.afterPut = () -> database.update("UPDATE video SET deleted_at=CURRENT_TIMESTAMP(6) WHERE id=?", videoId);
        worker.consume(message);
        assertEquals("RUNNING", taskState());
        assertEquals(1, storage.objects.size());
        clock.advance(1000);
        maintenance.run();
        assertEquals("CANCELLED", taskState());
        assertTrue(storage.objects.isEmpty());
    }

    @Test
    void committedOutputSurvivesCleanupAndDuplicateDelivery() {
        worker.consume(message);
        clock.advance(1000);
        maintenance.run();
        worker.consume(message);
        assertEquals(1, storage.objects.size());
        assertEquals("BOUND", database.queryForObject("SELECT state FROM processing_output WHERE task_id=?", String.class, message.taskId()));
    }

    @Test
    void failedCompletionRollsBackAssetTaskMetadataAndCleanupEventuallyRemovesOrphan() {
        storage.afterPut = () -> database.update("ALTER TABLE media_asset ADD CONSTRAINT reject_test_cover CHECK (kind <> 'COVER')");
        try {
            assertThrows(RuntimeException.class, () -> worker.consume(message));
            assertEquals("RUNNING", taskState());
            assertEquals("PROCESSING", videoState());
            assertNull(database.queryForObject("SELECT duration_millis FROM video WHERE id=?", Long.class, videoId));
        } finally { database.update("ALTER TABLE media_asset DROP CONSTRAINT reject_test_cover"); }
        clock.advance(1000);
        maintenance.run();
        assertTrue(storage.objects.isEmpty());
    }

    @Test
    void orphanDeleteFailureRetainsJournalAndRetriesAfterCleanupLease() {
        transactions.claim(message).orElseThrow();
        storage.objects.put("unrelated", new byte[]{1});
        clock.advance(1000);
        storage.failDelete = true;
        maintenance.run();
        assertEquals("CLEANING", database.queryForObject("SELECT state FROM processing_output WHERE task_id=?", String.class, message.taskId()));
        storage.failDelete = false;
        clock.advance(121);
        maintenance.run();
        assertEquals("DELETED", database.queryForObject("SELECT state FROM processing_output WHERE task_id=?", String.class, message.taskId()));
        assertTrue(storage.objects.containsKey("unrelated"));
    }

    @Test
    void malformedTaskIdentityIsRejectedAndOldGenerationIsIgnored() {
        assertEquals(ProcessingWorker.Decision.REJECT, worker.consume(new ProcessingMessage(1, message.taskId(), videoId + 999, 1)));
        assertEquals(ProcessingWorker.Decision.REJECT, worker.consume(new ProcessingMessage(1, message.taskId(), videoId, 2)));
        database.update("UPDATE video SET processing_generation=2 WHERE id=?", videoId);
        assertEquals(ProcessingWorker.Decision.ACK, worker.consume(message));
        assertTrue(storage.objects.isEmpty());
    }

    @Test
    void disabledAndBlockedOwnersCannotExecute() {
        database.update("UPDATE user_account SET status='DISABLED'");
        worker.consume(message);
        assertEquals("PENDING", taskState());
        database.update("UPDATE user_account SET status='ACTIVE'");
        database.update("UPDATE video SET moderation_blocked=TRUE WHERE id=?", videoId);
        worker.consume(message);
        assertEquals("PENDING", taskState());
        assertTrue(storage.objects.isEmpty());
    }

    @Test
    void coverUploadSucceededButResponseFailedIsRetriedAndOrphanIsRecoverable() {
        storage.afterPut = () -> { throw new ProcessingException(ProcessingFailure.STORAGE_UNAVAILABLE); };
        worker.consume(message);
        assertEquals("RETRY_WAIT", taskState());
        assertEquals(1, storage.objects.size());
        clock.advance(1000);
        maintenance.run();
        assertTrue(storage.objects.isEmpty());
        storage.afterPut = () -> { };
        worker.consume(message);
        assertEquals("SUCCEEDED", taskState());
        assertEquals(1, storage.objects.size());
    }

    @Test
    void completionCannotBindAKeyDifferentFromItsPersistedExecutionJournal() {
        var claim = transactions.claim(message).orElseThrow();
        var altered = new ExecutionClaim(claim.taskId(), claim.videoId(), claim.generation(), claim.executionToken(),
                claim.sourceKey(), claim.sourceBytes(), "covers/wrong.jpg");
        assertFalse(transactions.succeed(altered, new MediaMetadata(1000, 640, 360), 10));
        assertTrue(transactions.succeed(claim, new MediaMetadata(1000, 640, 360), 10));
    }

    @Test
    void expiredOutputAlreadyClaimedByCleanerCannotBind() {
        var claim = transactions.claim(message).orElseThrow();
        database.update("UPDATE processing_output SET expires_at=? WHERE execution_token=?", LocalDateTime.now(clock).minusSeconds(1), claim.executionToken());
        assertTrue(transactions.claimCleanup(claim.executionToken()).isPresent());
        assertFalse(transactions.succeed(claim, new MediaMetadata(1000, 640, 360), 10));
    }

    private String taskState() { return database.queryForObject("SELECT state FROM processing_task WHERE id=?", String.class, message.taskId()); }
    private String videoState() { return database.queryForObject("SELECT processing_status FROM video WHERE id=?", String.class, videoId); }

    static class TestClock extends Clock {
        final AtomicReference<Instant> instant = new AtomicReference<>(Instant.now());
        void advance(long seconds) { instant.updateAndGet(value -> value.plusSeconds(seconds)); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return instant.get(); }
    }
    static class FakeStorage implements ProcessingStorage {
        final Map<String, byte[]> objects = new HashMap<>();
        boolean failDelete;
        Runnable afterPut = () -> { };
        Runnable beforeDownload = () -> { };
        public void verify() { }
        public void download(String key, Path target, long bytes, long deadline) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            beforeDownload.run();
            try { Files.write(target, new byte[(int) bytes]); } catch (Exception exception) { throw new RuntimeException(exception); }
        }
        public void uploadCover(String key, Path file, long bytes, long deadline) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            try { objects.put(key, Files.readAllBytes(file)); } catch (Exception exception) { throw new RuntimeException(exception); }
            afterPut.run();
        }
        public void delete(String key) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            if (failDelete) throw new IllegalStateException("injected storage failure");
            objects.remove(key);
        }
    }
    static class FakeMedia implements MediaProcessor {
        ProcessingFailure failure;
        public void verify() { }
        public MediaMetadata process(Path source, Path cover, long deadline) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            if (failure != null) throw new ProcessingException(failure);
            try { Files.write(cover, new byte[]{(byte) 255, (byte) 216, (byte) 255, 1}); }
            catch (Exception exception) { throw new RuntimeException(exception); }
            return new MediaMetadata(1234, 640, 360);
        }
    }
    @TestConfiguration
    static class Fixtures {
        @Bean @Primary TestClock testClock() { return new TestClock(); }
        @Bean WorkerProperties workerProperties() {
            return new WorkerProperties("../.local/worker-test-temp", "test", "test", 3, 90, 150, 1, 1, 1048576, 1048576);
        }
        @Bean ExecutionTransactions transactions(ExecutionRepository repository, WorkerProperties properties, Clock clock) {
            return new ExecutionTransactions(repository, properties, clock);
        }
        @Bean FakeStorage storage() { return new FakeStorage(); }
        @Bean FakeMedia media() { return new FakeMedia(); }
        @Bean ProcessingWorkspace workspace(WorkerProperties properties) throws Exception { return new ProcessingWorkspace(properties); }
        @Bean ProcessingWorker worker(ExecutionTransactions transactions, FakeStorage storage, FakeMedia media,
                ProcessingWorkspace workspace, WorkerProperties properties) {
            return new ProcessingWorker(transactions, storage, media, workspace, properties);
        }
        @Bean ExecutionMaintenance maintenance(ExecutionRepository repository, ExecutionTransactions transactions,
                FakeStorage storage, ProcessingWorkspace workspace, Clock clock) {
            return new ExecutionMaintenance(repository, transactions, storage, workspace, clock);
        }
    }
}
