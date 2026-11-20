package com.creator.video;

import com.creator.video.application.processing.*;
import com.creator.video.domain.*;
import java.time.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"creator.processing.dispatch.enabled=true", "creator.processing.dispatch.provider=test",
        "creator.processing.dispatch.scan-milliseconds=3600000", "creator.processing.dispatch.batch-size=2"})
@ActiveProfiles("test")
@Import(DispatchIntegrationTest.Fixtures.class)
class DispatchIntegrationTest {
    @Autowired JdbcTemplate database;
    @Autowired DispatchService dispatcher;
    @Autowired DispatchTransactions transactions;
    @Autowired DispatchRepository repository;
    @Autowired DispatchProperties properties;
    @Autowired TestPublisher publisher;
    @Autowired MutableClock clock;
    long ownerId;
    long categoryId;

    @BeforeEach
    void reset() {
        clock.current.set(Instant.now());
        publisher.sent.clear();
        publisher.result = PublicationResult.CONFIRMED;
        publisher.failAfterSend = false;
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
        database.update("INSERT INTO user_account (username, password_hash) VALUES ('dispatch_author', 'not-used-by-these-tests')");
        ownerId = database.queryForObject("SELECT id FROM user_account WHERE username = 'dispatch_author'", Long.class);
        database.update("INSERT INTO video_category (name, name_key) VALUES ('dispatch', ?)", new byte[] {1});
        categoryId = database.queryForObject("SELECT id FROM video_category WHERE name = 'dispatch'", Long.class);
    }

    @Test
    void successfulPublicationDoesNotMarkVideoProcessedAndCanBeRedriven() {
        long taskId = task();
        dispatcher.run();
        assertEquals(1, publisher.sent.size());
        var first = publisher.sent.getFirst();
        assertEquals(taskId, first.id());
        assertEquals(1, first.message().schemaVersion());
        assertEquals("PENDING", state(taskId));
        assertNotNull(database.queryForObject("SELECT published_at FROM processing_task WHERE id = ?", LocalDateTime.class, taskId));
        assertEquals("UNPUBLISHED", database.queryForObject("SELECT publication_status FROM video WHERE id = ?", String.class, first.videoId()));
        assertEquals("PENDING", database.queryForObject("SELECT processing_status FROM video WHERE id = ?", String.class, first.videoId()));
        dispatcher.run();
        assertEquals(1, publisher.sent.size());
        clock.advance(properties.redriveSeconds() + 1);
        dispatcher.run();
        assertEquals(2, publisher.sent.size());
        assertEquals(first.message(), publisher.sent.getLast().message());
        assertNotEquals(first.dispatchToken(), publisher.sent.getLast().dispatchToken());
    }

    @ParameterizedTest
    @EnumSource(value = PublicationResult.class, names = {"UNROUTABLE", "BROKER_NACK", "CONFIRM_TIMEOUT", "BROKER_UNAVAILABLE", "INTERRUPTED"})
    void brokerFailuresStayPendingAndWaitForBackoff(PublicationResult result) {
        long taskId = task();
        publisher.result = result;
        dispatcher.run();
        assertEquals("PENDING", state(taskId));
        assertNull(database.queryForObject("SELECT published_at FROM processing_task WHERE id = ?", LocalDateTime.class, taskId));
        assertEquals(result.name(), database.queryForObject("SELECT last_dispatch_error FROM processing_task WHERE id = ?", String.class, taskId));
        dispatcher.run();
        assertEquals(1, publisher.sent.size());
        clock.advance(6);
        publisher.result = PublicationResult.CONFIRMED;
        dispatcher.run();
        assertEquals(2, publisher.sent.size());
        assertNull(database.queryForObject("SELECT last_dispatch_error FROM processing_task WHERE id = ?", String.class, taskId));
    }

    @Test
    void crashAfterSendLeavesLeaseAndRecoversUsingSameMessageIdentity() {
        long taskId = task();
        publisher.failAfterSend = true;
        dispatcher.run();
        var first = publisher.sent.getFirst();
        assertEquals("PENDING", state(taskId));
        assertNotNull(database.queryForObject("SELECT dispatch_token FROM processing_task WHERE id = ?", String.class, taskId));
        dispatcher.run();
        assertEquals(1, publisher.sent.size());
        clock.advance(properties.leaseSeconds() + 1);
        publisher.failAfterSend = false;
        dispatcher.run();
        assertEquals(2, publisher.sent.size());
        assertEquals(first.message(), publisher.sent.getLast().message());
        assertNotEquals(first.dispatchToken(), publisher.sent.getLast().dispatchToken());
    }

    @Test
    void twoIndependentTransactionsOnlyAcquireOneLease() throws Exception {
        long taskId = task();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = java.util.stream.IntStream.range(0, 2).mapToObj(index -> executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return transactions.claim(taskId);
            })).toList();
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            var first = results.get(0).get(10, TimeUnit.SECONDS);
            var second = results.get(1).get(10, TimeUnit.SECONDS);
            assertNotEquals(first.isPresent(), second.isPresent());
        }
        assertEquals(1L, database.queryForObject("SELECT dispatch_attempts FROM processing_task WHERE id = ?", Long.class, taskId));
    }

    @Test
    void expiredAndReplacedTokensCannotOverwriteNewPublication() {
        long taskId = task();
        var old = transactions.claim(taskId).orElseThrow();
        clock.advance(properties.leaseSeconds() + 1);
        assertFalse(transactions.complete(old, PublicationResult.CONFIRMED));
        var replacement = transactions.claim(taskId).orElseThrow();
        assertFalse(transactions.complete(old, PublicationResult.BROKER_NACK));
        assertTrue(transactions.complete(replacement, PublicationResult.CONFIRMED));
        assertFalse(transactions.complete(old, PublicationResult.CONFIRMED));
        assertNull(database.queryForObject("SELECT last_dispatch_error FROM processing_task WHERE id = ?", String.class, taskId));
        assertEquals(2L, replacement.dispatchAttempts());
    }

    @Test
    void terminalTasksAndUnavailableVideosAreNotDispatched() {
        for (String assignment : List.of("moderation_blocked = TRUE", "deleted_at = CURRENT_TIMESTAMP",
                "source_asset_id = NULL", "processing_status = 'READY'")) {
            long id = task();
            database.update("UPDATE video SET " + assignment + " WHERE id = (SELECT video_id FROM processing_task WHERE id = ?)", id);
        }
        long finished = task();
        database.update("UPDATE processing_task SET state = 'SUCCEEDED' WHERE id = ?", finished);
        dispatcher.run();
        assertTrue(publisher.sent.isEmpty());
        long disabled = task();
        database.update("UPDATE user_account SET status = 'DISABLED' WHERE id = ?", ownerId);
        assertTrue(transactions.claim(disabled).isEmpty());
        dispatcher.run();
        assertTrue(publisher.sent.isEmpty());
    }

    @Test
    void batchesAndRetryDelayAreBounded() {
        task(); task(); task();
        dispatcher.run();
        assertEquals(2, publisher.sent.size());
        dispatcher.run();
        assertEquals(3, publisher.sent.size());
        assertEquals(5, properties.retrySeconds(1));
        assertEquals(10, properties.retrySeconds(2));
        assertEquals(properties.retryMaxSeconds(), properties.retrySeconds(Long.MAX_VALUE));
    }

    @Test
    void consumerStateChangePreventsLatePublishMarker() {
        long id = task();
        var claim = transactions.claim(id).orElseThrow();
        database.update("UPDATE processing_task SET state = 'RUNNING' WHERE id = ?", id);
        assertFalse(transactions.complete(claim, PublicationResult.CONFIRMED));
        clock.advance(properties.redriveSeconds() + 1);
        dispatcher.run();
        assertTrue(publisher.sent.isEmpty());
    }

    private long task() {
        String key = UUID.randomUUID().toString();
        database.update("INSERT INTO video (owner_id, category_id, title, description, client_request_id, creation_fingerprint) VALUES (?, ?, 'fixture', '', ?, ?)",
                ownerId, categoryId, key, new byte[32]);
        long videoId = database.queryForObject("SELECT id FROM video WHERE client_request_id = ?", Long.class, key);
        database.update("INSERT INTO media_asset (video_id, kind, object_key, size_bytes) VALUES (?, 'SOURCE', ?, 1024)", videoId, "sources/" + key + ".mp4");
        long assetId = database.queryForObject("SELECT id FROM media_asset WHERE video_id = ?", Long.class, videoId);
        database.update("UPDATE video SET source_asset_id = ? WHERE id = ?", assetId, videoId);
        database.update("INSERT INTO processing_task (video_id, next_dispatch_at) VALUES (?, ?)", videoId, LocalDateTime.now(clock).minusSeconds(1));
        return database.queryForObject("SELECT id FROM processing_task WHERE video_id = ?", Long.class, videoId);
    }

    private String state(long id) { return database.queryForObject("SELECT state FROM processing_task WHERE id = ?", String.class, id); }

    @TestConfiguration
    static class Fixtures {
        @Bean @Primary MutableClock mutableClock() { return new MutableClock(); }
        @Bean TestPublisher testPublisher() { return new TestPublisher(); }
    }
    static class TestPublisher implements TaskPublisher {
        final List<DispatchClaim> sent = new CopyOnWriteArrayList<>();
        volatile PublicationResult result = PublicationResult.CONFIRMED;
        volatile boolean failAfterSend;
        @Override public PublicationResult publish(DispatchClaim claim) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            sent.add(claim);
            if (failAfterSend) throw new DataAccessResourceFailureException("Injected failure after publication");
            return result;
        }
    }
    static class MutableClock extends Clock {
        final AtomicReference<Instant> current = new AtomicReference<>(Instant.now());
        void advance(long seconds) { current.updateAndGet(value -> value.plusSeconds(seconds)); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { if (!zone.equals(getZone())) throw new IllegalArgumentException(); return this; }
        @Override public Instant instant() { return current.get(); }
    }
}
