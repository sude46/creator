package com.creator.video;

import com.creator.video.application.MediaSigner;
import com.creator.video.domain.*;
import com.creator.video.infrastructure.media.*;
import com.creator.video.infrastructure.upload.StorageProperties;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MinioMediaSignerTest {
    @Test
    void presignUsesBrowserEndpointRegionShortExpiryAndSignedResponseHeadersWithoutNetwork() {
        var storage = storage();
        var properties = new MediaProperties("https://media.example.invalid:9443", "test-region", 60);
        var clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        var signer = new MinioMediaSigner(storage, properties, clock);
        var grant = signer.sign(new MediaTarget("sources/private file.mp4", 100), MediaKind.SOURCE);
        var uri = URI.create(grant.url());
        assertEquals("media.example.invalid", uri.getHost());
        assertEquals(9443, uri.getPort());
        assertEquals("/creator-private/sources/private file.mp4", uri.getPath());
        var query = query(uri);
        assertEquals("60", query.get("X-Amz-Expires"));
        assertTrue(query.get("X-Amz-Credential").contains("/test-region/s3/aws4_request"));
        assertEquals("host", query.get("X-Amz-SignedHeaders"));
        assertEquals("video/mp4", query.get("response-content-type"));
        assertEquals("inline", query.get("response-content-disposition"));
        assertEquals("private, no-store", query.get("response-cache-control"));
        assertEquals(64, query.get("X-Amz-Signature").length());
        assertEquals(clock.instant().plusSeconds(60), grant.expiresAt());
        assertFalse(grant.toString().contains("X-Amz"));
        assertFalse(new MediaTarget("private-key", 100).toString().contains("private-key"));
        assertEquals("image/jpeg", query(URI.create(signer.sign(new MediaTarget("covers/cover.jpg", 10), MediaKind.COVER).url())).get("response-content-type"));
    }

    @Test
    void publicEndpointCannotContainCredentialsPrefixOrQuery() {
        for (String endpoint : List.of("https://name:password@example.test", "https://example.test/prefix", "https://example.test?token=1",
                "file:///tmp/object", "https://example.test#fragment")) {
            assertThrows(IllegalArgumentException.class, () -> new MinioMediaSigner(storage(), new MediaProperties(endpoint, "us-east-1", 60), Clock.systemUTC()));
        }
    }

    static StorageProperties storage() { return new StorageProperties("http://storage.internal:9000", "test-access", "test-secret-12345", "creator-private", 5, StorageProperties.IncompleteUploadCleanup.MINIO_DEFAULT); }
    static Map<String, String> query(URI uri) {
        var result = new HashMap<String, String>();
        for (String part : uri.getRawQuery().split("&")) {
            var pair = part.split("=", 2);
            result.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8), URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
        }
        return result;
    }
}
