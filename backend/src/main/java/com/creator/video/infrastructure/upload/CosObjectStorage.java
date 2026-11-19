package com.creator.video.infrastructure.upload;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.video.application.ObjectStorage;
import com.creator.video.application.processing.ProcessingException;
import com.creator.video.application.processing.ProcessingStorage;
import com.creator.video.application.processing.ProcessingWorker;
import com.creator.video.domain.ProcessingFailure;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.AbortIncompleteMultipartUpload;
import com.qcloud.cos.model.BucketLifecycleConfiguration;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.HeadBucketRequest;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.lifecycle.LifecyclePrefixPredicate;
import com.qcloud.cos.region.Region;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tencent COS adapter for the same storage contract as MinIO.
 *
 * <p>COS object operations use the native COS SDK because the MinIO Java SDK
 * signs path-style requests, which COS rejects in favour of virtual-hosted
 * bucket domains.</p>
 */
public class CosObjectStorage implements ObjectStorage, ProcessingStorage, AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(CosObjectStorage.class);
    private static final byte[] VERIFY_BODY = "creator storage verification".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    private final StorageProperties properties;
    private final COSClient client;
    private final HttpClient http;

    public CosObjectStorage(StorageProperties properties) {
        this.properties = properties;
        var config = new ClientConfig(new Region(properties.region()));
        config.setConnectionTimeout(5000);
        config.setSocketTimeout(15000);
        config.setRequestTimeout(properties.operationSeconds() * 1000);
        config.setMaxConnectionsCount(16);
        config.setMaxErrorRetry(1);
        config.setPrintShutdownStackTrace(false);
        this.client = new COSClient(new BasicCOSCredentials(properties.accessKey(), properties.secretKey()), config);
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Override
    public void verifyPrivateBucket() {
        if (properties.incompleteUploadCleanup() != StorageProperties.IncompleteUploadCleanup.BUCKET_LIFECYCLE) {
            throw new IllegalStateException("COS requires STORAGE_INCOMPLETE_UPLOAD_CLEANUP=BUCKET_LIFECYCLE");
        }
        String requirement = "bucket existence";
        try {
            client.headBucket(new HeadBucketRequest(properties.bucket()));
            requirement = "versioning OFF";
            var versioning = client.getBucketVersioningConfiguration(properties.bucket());
            String status = versioning == null ? null : versioning.getStatus();
            if (status != null && !status.isBlank() && !"Off".equalsIgnoreCase(status) && !"OFF".equalsIgnoreCase(status)) {
                throw new IllegalStateException("Storage bucket versioning must be off for source cleanup");
            }
            requirement = "one-day incomplete multipart lifecycle";
            var lifecycle = client.getBucketLifecycleConfiguration(properties.bucket());
            if (!abortsSources(lifecycle)) {
                throw new IllegalStateException("Storage requires a one-day abort-incomplete-multipart lifecycle for sources/");
            }
            requirement = "no anonymous bucket policy";
            try {
                var policy = client.getBucketPolicy(properties.bucket());
                if (policy != null && policy.getPolicyText() != null && !policy.getPolicyText().isBlank()) {
                    throw new IllegalStateException("Storage bucket must have no anonymous bucket policy");
                }
            } catch (CosServiceException exception) {
                if (exception.getStatusCode() != 404) {
                    LOGGER.warn("COS bucket policy could not be read [status={}, code={}]; anonymous probe will still run",
                            exception.getStatusCode(), exception.getErrorCode());
                }
            }
            verifyPrivateProbe();
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot verify COS private bucket requirement: " + requirement
                    + "; check configuration, region and application permissions", exception);
        }
    }

    private boolean abortsSources(BucketLifecycleConfiguration lifecycle) {
        if (lifecycle == null || lifecycle.getRules() == null) return false;
        for (var rule : lifecycle.getRules()) {
            if (!BucketLifecycleConfiguration.ENABLED.equalsIgnoreCase(rule.getStatus())) continue;
            AbortIncompleteMultipartUpload abort = rule.getAbortIncompleteMultipartUpload();
            if (abort == null || abort.getDaysAfterInitiation() != 1) continue;
            var filter = rule.getFilter();
            if (filter == null || filter.getPredicate() == null) return true;
            if (filter.getPredicate() instanceof LifecyclePrefixPredicate prefix) {
                String value = prefix.getPrefix();
                if (value == null || value.isEmpty() || "sources/".startsWith(value)) return true;
            }
        }
        return false;
    }

    private void verifyPrivateProbe() throws Exception {
        String key = "_verify/" + UUID.randomUUID() + ".txt";
        String host = properties.bucket() + ".cos." + properties.region() + ".myqcloud.com";
        URI uri = new URI("https", host, "/" + key, null);
        try {
            client.putObject(properties.bucket(), key,
                    new java.io.ByteArrayInputStream(VERIFY_BODY), metadata("text/plain", VERIFY_BODY.length));
            try (COSObject object = client.getObject(new GetObjectRequest(properties.bucket(), key));
                    COSObjectInputStream input = object.getObjectContent()) {
                byte[] read = input.readAllBytes();
                if (!java.util.Arrays.equals(read, VERIFY_BODY)) {
                    throw new IllegalStateException("Storage verification object was not readable");
                }
            }
            HttpResponse<Void> response = http.send(HttpRequest.newBuilder(uri).GET()
                    .timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() != 403 && response.statusCode() != 401) {
                throw new IllegalStateException("Storage bucket must reject anonymous object reads, got HTTP "
                        + response.statusCode());
            }
        } finally {
            try { client.deleteObject(properties.bucket(), key); }
            catch (RuntimeException exception) { LOGGER.warn("COS verification object cleanup failed", exception); }
        }
    }

    @Override
    public void put(String objectKey, Path file, long size) {
        try {
            var request = new PutObjectRequest(properties.bucket(), objectKey, file.toFile())
                    .withMetadata(metadata("video/mp4", size));
            request.setEnableResumableUpload(false);
            client.putObject(request);
        } catch (Exception exception) {
            LOGGER.warn("COS put failed [key={}, failure={}, message={}]", objectKey,
                    exception.getClass().getSimpleName(), exception.getMessage());
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            client.deleteObject(properties.bucket(), objectKey);
        } catch (Exception exception) {
            LOGGER.warn("COS delete failed [key={}, failure={}, message={}]", objectKey,
                    exception.getClass().getSimpleName(), exception.getMessage());
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    @Override
    public void verify() {
        verifyPrivateBucket();
    }

    @Override
    public void download(String key, Path target, long expectedBytes, long deadlineNanos) {
        try (COSObject object = client.getObject(new GetObjectRequest(properties.bucket(), key));
                COSObjectInputStream input = object.getObjectContent();
                var output = Files.newOutputStream(target, java.nio.file.StandardOpenOption.CREATE_NEW)) {
            long total = 0;
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) != -1) {
                ProcessingWorker.requireTime(deadlineNanos);
                total += count;
                if (total > expectedBytes) throw new ProcessingException(ProcessingFailure.INVALID_MEDIA);
                output.write(buffer, 0, count);
            }
            if (total != expectedBytes) throw new ProcessingException(ProcessingFailure.INVALID_MEDIA);
        } catch (ProcessingException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ProcessingException(ProcessingFailure.STORAGE_UNAVAILABLE);
        }
    }

    @Override
    public void uploadCover(String key, Path file, long bytes, long deadlineNanos) {
        ProcessingWorker.requireTime(deadlineNanos);
        if (bytes < 1 || bytes > 1048576) throw new ProcessingException(ProcessingFailure.PROCESS_FAILED);
        try {
            var request = new PutObjectRequest(properties.bucket(), key, file.toFile())
                    .withMetadata(metadata("image/jpeg", bytes));
            request.setEnableResumableUpload(false);
            client.putObject(request);
        } catch (Exception exception) {
            throw new ProcessingException(ProcessingFailure.STORAGE_UNAVAILABLE);
        }
    }

    private ObjectMetadata metadata(String contentType, long length) {
        var metadata = new ObjectMetadata();
        metadata.setContentType(contentType);
        metadata.setContentLength(length);
        return metadata;
    }

    @Override
    public void close() {
        client.shutdown();
        http.close();
    }
}
