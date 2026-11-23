package com.creator.video.infrastructure.media;

import com.creator.common.*;
import com.creator.video.application.MediaSigner;
import com.creator.video.domain.*;
import com.creator.video.infrastructure.upload.StorageProperties;
import io.minio.*;
import io.minio.http.Method;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

public class MinioMediaSigner implements MediaSigner {
    private final MinioClient client;
    private final StorageProperties storage;
    private final MediaProperties properties;
    private final Clock clock;

    public MinioMediaSigner(StorageProperties storage, MediaProperties properties, Clock clock) {
        var endpoint = URI.create(properties.publicEndpoint());
        if (!("https".equals(endpoint.getScheme()) || "http".equals(endpoint.getScheme())) || endpoint.getHost() == null
                || endpoint.getUserInfo() != null || endpoint.getQuery() != null || endpoint.getFragment() != null
                || (endpoint.getPath() != null && !endpoint.getPath().isEmpty() && !endpoint.getPath().equals("/"))) {
            throw new IllegalArgumentException("Media public endpoint must be an HTTP(S) origin without path or credentials");
        }
        this.storage = storage;
        this.properties = properties;
        this.clock = clock;
        client = MinioClient.builder().endpoint(properties.publicEndpoint()).region(properties.region())
                .credentials(storage.accessKey(), storage.secretKey()).build();
    }

    @Override
    public SignedMedia sign(MediaTarget target, MediaKind kind) {
        return sign(target, kind, false);
    }

    @Override
    public SignedMedia sign(MediaTarget target, MediaKind kind, boolean attachment) {
        int seconds = kind == MediaKind.COVER ? properties.coverLinkSeconds() : properties.linkSeconds();
        String disposition = attachment
                ? "attachment; filename=\"" + (kind == MediaKind.SOURCE ? "video.mp4" : "cover.jpg") + "\""
                : "inline";
        return signObject(target.objectKey(), kind.contentType(), disposition, seconds);
    }

    @Override
    public SignedMedia signHlsSegment(MediaTarget target, int seconds) {
        return signObject(target.objectKey(), "video/mp2t", "inline", seconds);
    }

    private SignedMedia signObject(String objectKey, String contentType, String disposition, int seconds) {
        Instant expires = clock.instant().plusSeconds(seconds);
        try {
            String url = client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().method(Method.GET)
                    .bucket(storage.bucket()).object(objectKey).expiry(seconds)
                    .extraQueryParams(Map.of("response-content-type", contentType,
                            "response-content-disposition", disposition,
                            "response-cache-control", "private, no-store")).build());
            return new SignedMedia(url, expires);
        } catch (Exception exception) { throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE); }
    }
}
