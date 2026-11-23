package com.creator.video.infrastructure.media;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.video.application.MediaSigner;
import com.creator.video.domain.MediaKind;
import com.creator.video.domain.MediaTarget;
import com.creator.video.infrastructure.upload.StorageProperties;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import com.qcloud.cos.model.ResponseHeaderOverrides;
import com.qcloud.cos.region.Region;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

public class CosMediaSigner implements MediaSigner {
    private final COSClient client;
    private final StorageProperties storage;
    private final MediaProperties properties;
    private final Clock clock;
    private final String expectedOrigin;

    public CosMediaSigner(StorageProperties storage, MediaProperties properties, Clock clock) {
        this.storage = storage;
        this.properties = properties;
        this.clock = clock;
        var config = new ClientConfig(new Region(storage.region()));
        config.setConnectionTimeout(5000);
        config.setSocketTimeout(15000);
        config.setRequestTimeout(storage.operationSeconds() * 1000);
        this.client = new COSClient(new BasicCOSCredentials(storage.accessKey(), storage.secretKey()), config);
        this.expectedOrigin = "https://" + storage.bucket() + ".cos." + storage.region() + ".myqcloud.com";
        var publicEndpoint = URI.create(properties.publicEndpoint());
        if (!"https".equals(publicEndpoint.getScheme()) || !expectedOrigin.equals(properties.publicEndpoint())) {
            throw new IllegalArgumentException("COS media public endpoint must be " + expectedOrigin);
        }
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
            var request = new GeneratePresignedUrlRequest(storage.bucket(), objectKey, HttpMethodName.GET);
            request.setExpiration(Date.from(expires));
            request.setResponseHeaders(new ResponseHeaderOverrides()
                    .withContentType(contentType)
                    .withContentDisposition(disposition)
                    .withCacheControl("private, no-store"));
            String url = client.generatePresignedUrl(request).toString();
            if (!url.startsWith(expectedOrigin + "/")) throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
            return new SignedMedia(url, expires);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }
}
