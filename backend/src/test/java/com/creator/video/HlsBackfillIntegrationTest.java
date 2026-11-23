package com.creator.video;

import com.creator.catalog.application.CategoryService;
import com.creator.identity.application.AccountQueryService;
import com.creator.identity.application.BootstrapAdmin;
import com.creator.identity.application.RegisterUser;
import com.creator.identity.domain.*;
import com.creator.video.application.*;
import com.creator.video.application.processing.*;
import com.creator.video.domain.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class HlsBackfillIntegrationTest {
    @Autowired JdbcTemplate database;
    @Autowired DraftService drafts;
    @Autowired CategoryService categories;
    @Autowired RegisterUser registration;
    @Autowired BootstrapAdmin bootstrap;
    @Autowired AccountRepository accounts;
    @Autowired VideoQueryRepository videoQueries;
    @Autowired HlsBackfillService backfill;
    @Autowired HlsBackfillRepository hlsTasks;
    @Autowired Clock clock;
    @TempDir Path temp;
    Actor author;
    Actor admin;
    long categoryId;

    @BeforeEach
    void reset() throws Exception {
        database.update("DELETE FROM operation_log");
        database.update("DELETE FROM hls_backfill_task");
        database.update("DELETE FROM video_subtitle");
        database.update("DELETE FROM video_hls_segment");
        database.update("DELETE FROM video_hls");
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
        database.update("DELETE FROM video_collection_item");
        database.update("DELETE FROM video_collection");
        database.update("DELETE FROM content_appeal");
        database.update("DELETE FROM video");
        database.update("DELETE FROM video_category");
        database.update("DELETE FROM user_profile");
        database.update("DELETE FROM user_follow");
        database.update("DELETE FROM user_account");
        registration.execute("backfill_author", "SafePassword123");
        bootstrap.execute("backfill_admin", "SafePassword123");
        author = actor("backfill_author");
        admin = actor("backfill_admin");
        categoryId = categories.create(admin, "backfill", 0).id();
    }

    @Test
    void backfillAddsHlsWithoutChangingPublicationOrReReviewing() throws Exception {
        long video = published("需要回填的作品");
        var before = database.queryForMap("SELECT review_status, publication_status, processing_status, version FROM video WHERE id=?", video);
        var storage = new FakeStorage();
        var properties = properties();
        var worker = new HlsBackfillWorker(new HlsBackfillTransactions(hlsTasks, properties, clock), storage,
                new FakeMedia(), new ProcessingWorkspace(properties), properties);

        assertEquals(1, backfill.enqueue(admin, 20, null).enqueued());
        assertEquals("PENDING", database.queryForObject(
                "SELECT state FROM hls_backfill_task WHERE video_id=?", String.class, video));
        worker.runOnce();

        assertEquals("DONE", database.queryForObject(
                "SELECT state FROM hls_backfill_task WHERE video_id=?", String.class, video));
        assertEquals(1L, database.queryForObject("SELECT COUNT(*) FROM video_hls WHERE video_id=?", Long.class, video));
        assertEquals(1L, database.queryForObject("SELECT COUNT(*) FROM video_hls_segment WHERE video_id=?", Long.class, video));
        assertEquals(2, storage.objects.size());
        assertTrue(videoQueries.findPublic(video).orElseThrow().hasHls());
        assertEquals(before, database.queryForMap(
                "SELECT review_status, publication_status, processing_status, version FROM video WHERE id=?", video));
        assertEquals(0, backfill.enqueue(admin, 20, null).enqueued());
    }

    @Test
    void failedBackfillIsRetriedBoundedlyAndDoesNotBlockMp4() throws Exception {
        long video = published("回填失败作品");
        var properties = properties();
        var worker = new HlsBackfillWorker(new HlsBackfillTransactions(hlsTasks, properties, clock),
                new FakeStorage(), new EmptyMedia(), new ProcessingWorkspace(properties), properties);
        assertEquals(1, backfill.enqueue(admin, 20, video).enqueued());
        worker.runOnce();
        assertEquals("RETRY_WAIT", database.queryForObject(
                "SELECT state FROM hls_backfill_task WHERE video_id=?", String.class, video));
        assertEquals(1, database.queryForObject(
                "SELECT attempts FROM hls_backfill_task WHERE video_id=?", Integer.class, video));
        assertFalse(videoQueries.findPublic(video).orElseThrow().hasHls());
        assertEquals(0L, database.queryForObject("SELECT COUNT(*) FROM video_hls WHERE video_id=?", Long.class, video));
    }

    private long published(String title) {
        long id = drafts.create(author, UUID.randomUUID().toString(), new VideoMetadata(title, "description", categoryId)).id();
        database.update("INSERT INTO media_asset (video_id,kind,object_key,size_bytes) VALUES (?,'SOURCE',?,64)", id, "sources/" + UUID.randomUUID() + ".mp4");
        long asset = database.queryForObject("SELECT id FROM media_asset WHERE video_id=?", Long.class, id);
        database.update("INSERT INTO media_asset (video_id,kind,object_key,size_bytes) VALUES (?,'COVER',?,10)", id, "covers/" + UUID.randomUUID() + ".jpg");
        database.update("UPDATE video SET source_asset_id=?,processing_status='READY',duration_millis=1000,media_width=320,media_height=240,review_status='APPROVED',publication_status='PUBLISHED',published_at=CURRENT_TIMESTAMP(6) WHERE id=?", asset, id);
        database.update("INSERT INTO processing_task (video_id,state) VALUES (?,'SUCCEEDED')", id);
        return id;
    }
    private Actor actor(String name) { var account = accounts.findSummary(name).orElseThrow(); return new Actor(account.id(), name, account.role(), account.adminScope()); }
    private WorkerProperties properties() { return new WorkerProperties(temp.toString(), "test", "test", 3, 90, 150, 1, 1, 1048576, 1048576); }
    static class EmptyMedia implements MediaProcessor {
        public void verify() { }
        public MediaMetadata process(Path source, Path cover, long deadline) { throw new UnsupportedOperationException(); }
        public List<ProcessedMedia.HlsVariant> processHlsOnly(Path source, Path hlsDirectory, long deadline) {
            return List.of();
        }
    }
    static class FakeStorage implements ProcessingStorage {
        final Map<String, byte[]> objects = new HashMap<>();
        public void verify() { }
        public void download(String key, Path target, long bytes, long deadline) {
            try { Files.write(target, new byte[(int) bytes]); } catch (IOException exception) { throw new RuntimeException(exception); }
        }
        public void uploadCover(String key, Path file, long bytes, long deadline) { throw new UnsupportedOperationException(); }
        public void uploadHls(String key, Path file, long bytes, String contentType, long deadline) {
            try { objects.put(key, Files.readAllBytes(file)); } catch (IOException exception) { throw new RuntimeException(exception); }
        }
        public void delete(String key) { objects.remove(key); }
    }
    static class FakeMedia implements MediaProcessor {
        public void verify() { }
        public MediaMetadata process(Path source, Path cover, long deadline) { throw new UnsupportedOperationException(); }
        public List<ProcessedMedia.HlsVariant> processHlsOnly(Path source, Path hlsDirectory, long deadline) {
            try {
                Path directory = hlsDirectory.resolve("240p");
                Files.createDirectories(directory);
                Files.writeString(directory.resolve("index.m3u8"), "#EXTM3U\n#EXT-X-TARGETDURATION:4\n#EXTINF:3.0,\nseg-00000.ts\n#EXT-X-ENDLIST\n");
                Files.write(directory.resolve("seg-00000.ts"), new byte[]{1, 2, 3});
                return List.of(new ProcessedMedia.HlsVariant("240p", 700, 320, 240,
                        "#EXTM3U\n#EXT-X-TARGETDURATION:4\n#EXTINF:3.0,\nseg-00000.ts\n#EXT-X-ENDLIST\n"));
            } catch (IOException exception) { throw new RuntimeException(exception); }
        }
    }
}
