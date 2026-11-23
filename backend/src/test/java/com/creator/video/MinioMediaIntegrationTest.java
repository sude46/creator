package com.creator.video;

import com.creator.video.domain.*;
import com.creator.video.infrastructure.media.*;
import com.creator.video.infrastructure.upload.*;
import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "CREATOR_MINIO_IT_PORT", matches = "[0-9]+")
class MinioMediaIntegrationTest {
    @TempDir Path directory;

    @Test
    void realPrivateMinioAcceptsSignedRangeJpegAndRejectsAnonymousTamperedAndExpiredLinks() throws Exception {
        String endpoint = "http://127.0.0.1:" + System.getenv("CREATOR_MINIO_IT_PORT");
        String bucket = "creator-media-it-" + UUID.randomUUID();
        var client = MinioClient.builder().endpoint(endpoint).region("us-east-1")
                .credentials("creator_test", "integration-only-password").build();
        var properties = new StorageProperties(endpoint, "creator_test", "integration-only-password", bucket, "us-east-1", 5, StorageProperties.IncompleteUploadCleanup.MINIO_DEFAULT);
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        byte[] content = new byte[4096];
        new Random(1).nextBytes(content);
        byte[] cover = {(byte)255, (byte)216, (byte)255, 1};
        client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        try (var storage = new MinioObjectStorage(properties)) {
            storage.verifyPrivateBucket();
            var source = Files.write(directory.resolve("source.mp4"), content);
            storage.put("sources/sample.mp4", source, content.length);
            var downloaded = directory.resolve("download.mp4");
            storage.download("sources/sample.mp4", downloaded, content.length, System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10));
            assertArrayEquals(content, Files.readAllBytes(downloaded));
            var coverFile = Files.write(directory.resolve("cover.jpg"), cover);
            storage.uploadCover("covers/sample.jpg", coverFile, cover.length, System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10));
            var signer = new MinioMediaSigner(properties, new MediaProperties(endpoint, "us-east-1", 60, 300, 900), Clock.systemUTC());
            var signed = signer.sign(new MediaTarget("sources/sample.mp4", content.length), MediaKind.SOURCE);
            var range = http.send(HttpRequest.newBuilder(URI.create(signed.url())).timeout(Duration.ofSeconds(5))
                    .header("Range", "bytes=32-63").build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(206, range.statusCode());
            assertEquals("bytes 32-63/4096", range.headers().firstValue("Content-Range").orElseThrow());
            assertEquals("video/mp4", range.headers().firstValue("Content-Type").orElseThrow());
            assertEquals("private, no-store", range.headers().firstValue("Cache-Control").orElseThrow());
            assertArrayEquals(Arrays.copyOfRange(content, 32, 64), range.body());
            var coverLink = signer.sign(new MediaTarget("covers/sample.jpg", cover.length), MediaKind.COVER);
            var jpeg = get(http, coverLink.url());
            assertEquals(200, jpeg.statusCode());
            assertEquals("image/jpeg", jpeg.headers().firstValue("Content-Type").orElseThrow());
            assertArrayEquals(cover, jpeg.body());
            assertEquals(403, get(http, endpoint + "/" + bucket + "/sources/sample.mp4").statusCode());
            assertEquals(403, get(http, signed.url().replace("X-Amz-Expires=60", "X-Amz-Expires=61")).statusCode());
            assertEquals(403, get(http, signed.url().replace("127.0.0.1", "localhost")).statusCode());
            String expired = client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().method(Method.GET)
                    .bucket(bucket).object("sources/sample.mp4").expiry(1).build());
            Thread.sleep(2100);
            assertEquals(403, get(http, expired).statusCode());
        } finally {
            for (String key : List.of("sources/sample.mp4", "covers/sample.jpg")) client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
            client.removeBucket(RemoveBucketArgs.builder().bucket(bucket).build());
        }
    }

    private HttpResponse<byte[]> get(HttpClient client, String url) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).build(), HttpResponse.BodyHandlers.ofByteArray());
    }
}
