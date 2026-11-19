package com.creator.video;

import com.creator.video.application.processing.ProcessingStorage;
import com.creator.video.domain.ProcessingMessage;
import com.creator.video.infrastructure.media.BoundedMediaProcess;
import com.creator.video.infrastructure.messaging.ProcessingTopology;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.*;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "creator.storage.provider=test", "creator.processing.worker.temp-directory=../.local/worker-runtime-test-temp"})
@ActiveProfiles({"test", "worker"})
@Import(WorkerRuntimeIntegrationTest.Fixtures.class)
@DirtiesContext
@EnabledIfEnvironmentVariable(named = "CREATOR_RABBIT_IT_PORT", matches = "[0-9]+")
@EnabledIfEnvironmentVariable(named = "CREATOR_FFMPEG_IT_DIR", matches = ".+")
class WorkerRuntimeIntegrationTest {
    @Autowired JdbcTemplate database;
    @Autowired ApplicationContext context;
    @Autowired FixtureStorage storage;
    @Autowired @Qualifier("processingRabbitTemplate") RabbitTemplate publisher;
    @Autowired ObjectMapper json;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", () -> "127.0.0.1");
        registry.add("spring.rabbitmq.port", () -> System.getenv("CREATOR_RABBIT_IT_PORT"));
        registry.add("spring.rabbitmq.username", () -> "creator_test");
        registry.add("spring.rabbitmq.password", () -> "integration-only-password");
        registry.add("spring.rabbitmq.virtual-host", () -> "creator_worker_it");
        registry.add("creator.processing.worker.ffprobe", () -> executable("ffprobe"));
        registry.add("creator.processing.worker.ffmpeg", () -> executable("ffmpeg"));
    }
    static String executable(String name) { return Path.of(System.getenv("CREATOR_FFMPEG_IT_DIR"), MediaProcessTest.nativeName(name)).toString(); }

    @Test
    void nonWebWorkerConsumesRealQueueRunsFfmpegBindsResultsAndDeadLettersPoison() throws Exception {
        assertFalse(context instanceof ServletWebServerApplicationContext);
        var directory = Files.createTempDirectory(Path.of("../.local"), "runtime-media-").toAbsolutePath();
        try {
            var source = directory.resolve("fixture.mp4");
            var generated = new BoundedMediaProcess().run(List.of(executable("ffmpeg"), "-v", "error", "-f", "lavfi", "-i",
                    "testsrc=size=320x240:rate=30", "-t", "1", "-c:v", "libx264", "-threads", "1", "-pix_fmt", "yuv420p", source.toString()),
                    directory, System.nanoTime() + TimeUnit.SECONDS.toNanos(15));
            assertEquals(0, generated.exitCode());
            storage.source = Files.readAllBytes(source);
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
            database.update("DELETE FROM user_account");
            database.update("INSERT INTO user_account (username,password_hash) VALUES ('runtime_author','not-a-password')");
            long owner = database.queryForObject("SELECT id FROM user_account WHERE username='runtime_author'", Long.class);
            database.update("INSERT INTO video_category (name,name_key) VALUES ('runtime',?)", new byte[]{9});
            long category = database.queryForObject("SELECT id FROM video_category WHERE name='runtime'", Long.class);
            database.update("INSERT INTO video (owner_id,category_id,title,description,client_request_id,creation_fingerprint) VALUES (?,?,'sample','',?,?)",
                    owner, category, UUID.randomUUID().toString(), new byte[32]);
            long video = database.queryForObject("SELECT id FROM video WHERE owner_id=?", Long.class, owner);
            database.update("INSERT INTO media_asset (video_id,kind,object_key,size_bytes) VALUES (?,'SOURCE','sources/runtime.mp4',?)", video, storage.source.length);
            long asset = database.queryForObject("SELECT id FROM media_asset WHERE video_id=?", Long.class, video);
            database.update("UPDATE video SET source_asset_id=? WHERE id=?", asset, video);
            database.update("INSERT INTO processing_task (video_id) VALUES (?)", video);
            long task = database.queryForObject("SELECT id FROM processing_task WHERE video_id=?", Long.class, video);
            var body = json.writeValueAsBytes(new ProcessingMessage(1, task, video, 1));
            publisher.send(ProcessingTopology.EXCHANGE, ProcessingTopology.ROUTING_KEY, new Message(body, new MessageProperties()));
            await(() -> "SUCCEEDED".equals(database.queryForObject("SELECT state FROM processing_task WHERE id=?", String.class, task)));
            publisher.send(ProcessingTopology.EXCHANGE, ProcessingTopology.ROUTING_KEY, new Message(body, new MessageProperties()));
            publisher.send(ProcessingTopology.EXCHANGE, ProcessingTopology.ROUTING_KEY, new Message("invalid".getBytes(java.nio.charset.StandardCharsets.UTF_8), new MessageProperties()));
            await(() -> publisher.receive(ProcessingTopology.DEAD_QUEUE) != null);
            assertEquals(1, database.queryForObject("SELECT execution_attempts FROM processing_task WHERE id=?", Integer.class, task));
            assertEquals("READY", database.queryForObject("SELECT processing_status FROM video WHERE id=?", String.class, video));
            assertEquals(1, storage.covers.size());
            assertTrue(storage.covers.values().iterator().next().length > 10);
        } finally {
            Files.deleteIfExists(directory.resolve("fixture.mp4"));
            Files.deleteIfExists(directory);
        }
    }

    private void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) fail("Worker did not reach expected outcome");
            Thread.sleep(50);
        }
    }
    static class FixtureStorage implements ProcessingStorage {
        volatile byte[] source;
        final Map<String, byte[]> covers = new ConcurrentHashMap<>();
        public void verify() { }
        public void download(String key, Path target, long expected, long deadline) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(expected, source.length);
            try { Files.write(target, source); } catch (Exception exception) { throw new RuntimeException(exception); }
        }
        public void uploadCover(String key, Path file, long bytes, long deadline) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            try { covers.put(key, Files.readAllBytes(file)); } catch (Exception exception) { throw new RuntimeException(exception); }
        }
        public void delete(String key) { covers.remove(key); }
    }
    @TestConfiguration
    static class Fixtures {
        @Bean FixtureStorage fixtureStorage() { return new FixtureStorage(); }
    }
}
