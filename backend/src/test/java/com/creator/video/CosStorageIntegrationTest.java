package com.creator.video;

import static org.junit.jupiter.api.Assertions.*;

import com.creator.video.domain.MediaKind;
import com.creator.video.domain.MediaTarget;
import com.creator.video.infrastructure.media.CosMediaSigner;
import com.creator.video.infrastructure.media.MediaProperties;
import com.creator.video.infrastructure.upload.CosObjectStorage;
import com.creator.video.infrastructure.upload.StorageProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "CREATOR_COS_IT", matches = "true")
class CosStorageIntegrationTest {
    @Test
    void cosProviderVerifiesPrivateBucketAndServesSignedRange() throws Exception {
        String bucket = System.getenv("CREATOR_COS_BUCKET");
        String region = System.getenv("CREATOR_COS_REGION");
        String publicEndpoint = System.getenv("CREATOR_COS_PUBLIC_ENDPOINT");
        var storage = new StorageProperties(System.getenv("CREATOR_COS_ENDPOINT"), System.getenv("CREATOR_COS_SECRET_ID"),
                System.getenv("CREATOR_COS_SECRET_KEY"), bucket, region, 30,
                StorageProperties.IncompleteUploadCleanup.BUCKET_LIFECYCLE);
        String key = "_verify/provider-" + System.nanoTime() + ".mp4";
        Path source = Files.createTempFile("cos-source-", ".mp4");
        Path downloaded = Files.createTempFile("cos-download-", ".mp4");
        Files.deleteIfExists(downloaded);
        try {
            Files.write(source, new byte[128 * 1024]);
            try (var cos = new CosObjectStorage(storage)) {
                cos.verifyPrivateBucket();
                cos.put(key, source, Files.size(source));
                cos.download(key, downloaded, Files.size(source), System.nanoTime() + Duration.ofSeconds(30).toNanos());
                assertEquals(Files.size(source), Files.size(downloaded));
            }
            var signer = new CosMediaSigner(storage,
                    new MediaProperties(publicEndpoint, region, 60, 300), Clock.systemUTC());
            var signed = signer.sign(new MediaTarget(key, Files.size(source)), MediaKind.SOURCE);
            assertEquals(bucket + ".cos." + region + ".myqcloud.com", URI.create(signed.url()).getHost());
            HttpClient http = HttpClient.newHttpClient();
            var range = http.send(HttpRequest.newBuilder(URI.create(signed.url())).header("Range", "bytes=0-9")
                    .timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(206, range.statusCode());
            assertEquals(10, range.body().length);
            var anonymous = http.send(HttpRequest.newBuilder(URI.create(signed.url().split("\\?")[0]))
                    .timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.discarding());
            assertEquals(403, anonymous.statusCode());
        } finally {
            try (var cos = new CosObjectStorage(storage)) { cos.delete(key); }
            catch (RuntimeException ignored) { }
            Files.deleteIfExists(source);
            Files.deleteIfExists(downloaded);
        }
    }
}
