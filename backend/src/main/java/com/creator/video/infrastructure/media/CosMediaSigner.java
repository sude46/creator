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
        Instant expires = clock.instant().plusSeconds(seconds);
        String disposition = attachment
                ? "attachment; filename=\"" + (kind == MediaKind.SOURCE ? "video.mp4" : "cover.jpg") + "\""
                : "inline";
        try {
            var request = new GeneratePresignedUrlRequest(storage.bucket(), target.objectKey(), HttpMethodName.GET);
            request.setExpiration(Date.from(expires));
            request.setResponseHeaders(new ResponseHeaderOverrides()
                    .withContentType(kind.contentType())
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
