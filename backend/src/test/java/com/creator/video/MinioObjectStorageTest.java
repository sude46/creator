package com.creator.video;

import com.creator.video.infrastructure.upload.MinioObjectStorage;
import com.creator.video.infrastructure.upload.StorageProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class MinioObjectStorageTest {
    @TempDir Path directory;

    @Test
    void realSdkSignsPrivateBucketCheckPutAndDeleteRequests() throws Exception {
        var uploaded = new AtomicReference<byte[]>();
        var deleted = new AtomicBoolean();
        var signed = new AtomicBoolean(true);
        var server = server(false, true, uploaded, deleted, signed);
        try (var storage = new MinioObjectStorage(properties(server))) {
            storage.verifyPrivateBucket();
            Path file = Files.write(directory.resolve("source.mp4"), UploadIntegrationTest.mp4(1024));
            storage.put("sources/test.mp4", file, Files.size(file));
            storage.delete("sources/test.mp4");
            assertArrayEquals(Files.readAllBytes(file), uploaded.get());
            assertTrue(deleted.get());
            assertTrue(signed.get());
        } finally { server.stop(0); }
    }

    @Test
    void realSdkBoundsDownloadsAndUsesJpegCoverContentType() throws Exception {
        byte[] payload = new byte[] {1, 2, 3, 4};
        var contentType = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                byte[] response;
                if (exchange.getRequestURI().getQuery() != null) {
                    response = "<LocationConstraint xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">us-east-1</LocationConstraint>".getBytes(StandardCharsets.UTF_8);
                } else if (exchange.getRequestMethod().equals("PUT")) {
                    contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
                    exchange.getRequestBody().readAllBytes();
                    exchange.getResponseHeaders().set("ETag", "\"test-etag\"");
                    exchange.sendResponseHeaders(200, -1);
                    return;
                } else { response = payload; }
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            } finally { exchange.close(); }
        });
        server.start();
        try (var storage = new MinioObjectStorage(properties(server))) {
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            var target = directory.resolve("download.mp4");
            storage.download("sources/test.mp4", target, payload.length, deadline);
            assertArrayEquals(payload, Files.readAllBytes(target));
            var exception = assertThrows(com.creator.video.application.processing.ProcessingException.class,
                    () -> storage.download("sources/test.mp4", directory.resolve("too-large.mp4"), 3, deadline));
            assertEquals(com.creator.video.domain.ProcessingFailure.INVALID_MEDIA, exception.failure());
            assertThrows(com.creator.video.application.processing.ProcessingException.class,
                    () -> storage.download("sources/test.mp4", directory.resolve("too-short.mp4"), 5, deadline));
            storage.uploadCover("covers/test.jpg", target, payload.length, deadline);
            assertEquals("image/jpeg", contentType.get());
        } finally { server.stop(0); }
    }

    @Test
    void bucketWithPolicyIsRejectedBeforeAcceptingUploads() throws Exception {
        var server = server(true, true, new AtomicReference<>(), new AtomicBoolean(), new AtomicBoolean(true));
        try (var storage = new MinioObjectStorage(properties(server))) {
            assertThrows(IllegalStateException.class, storage::verifyPrivateBucket);
        } finally { server.stop(0); }
    }

    @Test
    void operationDeadlineCoversMultipleSdkHttpCalls() throws Exception {
        var server = server(false, true, new AtomicReference<>(), new AtomicBoolean(), new AtomicBoolean(true), 3000);
        try (var storage = new MinioObjectStorage(properties(server))) {
            long started = System.nanoTime();
            assertThrows(IllegalStateException.class, storage::verifyPrivateBucket);
            long elapsedMillis = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            assertTrue(elapsedMillis >= 4000 && elapsedMillis < 5900, "Unexpected operation duration: " + elapsedMillis);
        } finally { server.stop(0); }
    }

    @Test
    void minioDefaultCleanupModeDoesNotRequireBucketLifecycle() throws Exception {
        var server = server(false, false, new AtomicReference<>(), new AtomicBoolean(), new AtomicBoolean(true));
        try (var storage = new MinioObjectStorage(properties(server))) {
            storage.verifyPrivateBucket();
        } finally { server.stop(0); }
    }

    @Test
    void bucketLifecycleModeRejectsMissingIncompleteMultipartCleanup() throws Exception {
        var server = server(false, false, new AtomicReference<>(), new AtomicBoolean(), new AtomicBoolean(true));
        try (var storage = new MinioObjectStorage(properties(server, StorageProperties.IncompleteUploadCleanup.BUCKET_LIFECYCLE))) {
            assertThrows(IllegalStateException.class, storage::verifyPrivateBucket);
        } finally { server.stop(0); }
    }

    @Test
    void bucketLifecycleModeAcceptsOneDayAbortRule() throws Exception {
        var server = server(false, true, new AtomicReference<>(), new AtomicBoolean(), new AtomicBoolean(true));
        try (var storage = new MinioObjectStorage(properties(server, StorageProperties.IncompleteUploadCleanup.BUCKET_LIFECYCLE))) {
            storage.verifyPrivateBucket();
        } finally { server.stop(0); }
    }

    private StorageProperties properties(HttpServer server) {
        return properties(server, StorageProperties.IncompleteUploadCleanup.MINIO_DEFAULT);
    }

    private StorageProperties properties(HttpServer server, StorageProperties.IncompleteUploadCleanup cleanup) {
        return new StorageProperties("http://127.0.0.1:" + server.getAddress().getPort(), "test-access", "test-secret-12345",
                "creator-private", 5, cleanup);
    }

    private HttpServer server(boolean publicPolicy, boolean abortIncomplete, AtomicReference<byte[]> uploaded, AtomicBoolean deleted,
            AtomicBoolean signed) throws Exception {
        return server(publicPolicy, abortIncomplete, uploaded, deleted, signed, 0);
    }

    private HttpServer server(boolean publicPolicy, boolean abortIncomplete, AtomicReference<byte[]> uploaded, AtomicBoolean deleted,
            AtomicBoolean signed, long delayMillis) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                if (delayMillis > 0) {
                    try { Thread.sleep(delayMillis); }
                    catch (InterruptedException exception) { Thread.currentThread().interrupt(); return; }
                }
                String authorization = exchange.getRequestHeaders().getFirst("Authorization");
                if (authorization == null || !authorization.startsWith("AWS4-HMAC-SHA256 ")) signed.set(false);
                String query = exchange.getRequestURI().getQuery();
                if (query != null) query = query.replaceFirst("=$", "");
                String body = "";
                int status = 200;
                String method = exchange.getRequestMethod();
                if (method.equals("HEAD")) {
                    exchange.sendResponseHeaders(200, -1);
                    return;
                } else if ("location".equals(query)) {
                    body = "<LocationConstraint xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">us-east-1</LocationConstraint>";
                } else if ("versioning".equals(query)) {
                    body = "<VersioningConfiguration xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\"/>";
                } else if ("lifecycle".equals(query)) {
                    body = "<LifecycleConfiguration xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">"
                            + (abortIncomplete ? "<Rule><ID>abort-incomplete</ID><Status>Enabled</Status><Filter><Prefix>sources/</Prefix></Filter>"
                            + "<AbortIncompleteMultipartUpload><DaysAfterInitiation>1</DaysAfterInitiation></AbortIncompleteMultipartUpload></Rule>" : "")
                            + "</LifecycleConfiguration>";
                } else if ("policy".equals(query)) {
                    if (publicPolicy) body = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\"}]}";
                    else { status = 404; body = "<Error><Code>NoSuchBucketPolicy</Code><Message>No policy</Message></Error>"; }
                } else if (method.equals("PUT")) {
                    uploaded.set(exchange.getRequestBody().readAllBytes());
                    exchange.getResponseHeaders().set("ETag", "\"test-etag\"");
                } else if (method.equals("DELETE")) {
                    deleted.set(true);
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/xml");
                exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
                if (bytes.length > 0) exchange.getResponseBody().write(bytes);
            } finally { exchange.close(); }
        });
        server.start();
        return server;
    }
}
