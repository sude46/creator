package com.creator.video.infrastructure.upload;

import com.creator.common.BusinessException;
import com.creator.common.ErrorCode;
import com.creator.video.application.ObjectStorage;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Call;
import okhttp3.Request;

public class MinioObjectStorage implements ObjectStorage, AutoCloseable {
    private final StorageProperties properties;
    private final OkHttpClient http;

    public MinioObjectStorage(StorageProperties properties) {
        this.properties = properties;
        http = new OkHttpClient.Builder().connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(15)).writeTimeout(Duration.ofSeconds(15))
                .callTimeout(Duration.ofSeconds(properties.operationSeconds())).retryOnConnectionFailure(false).build();
    }

    @Override
    public void verifyPrivateBucket() {
        String requirement = "bucket existence";
        try {
            var client = client();
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(properties.bucket()).build())) {
                throw new IllegalStateException("Configured private storage bucket does not exist");
            }
            requirement = "versioning OFF";
            if (client.getBucketVersioning(GetBucketVersioningArgs.builder().bucket(properties.bucket()).build()).status()
                    != io.minio.messages.VersioningConfiguration.Status.OFF) {
                throw new IllegalStateException("Storage bucket versioning must be off for source cleanup");
            }
            requirement = "one-day incomplete multipart lifecycle";
            var lifecycle = client.getBucketLifecycle(GetBucketLifecycleArgs.builder().bucket(properties.bucket()).build());
            boolean abortsIncomplete = lifecycle.rules() != null && lifecycle.rules().stream().anyMatch(rule -> {
                var filter = rule.filter();
                boolean coversSources = filter == null || (filter.prefix() != null && "sources/".startsWith(filter.prefix())
                        && filter.andOperator() == null && filter.tag() == null
                        && filter.objectSizeLessThan() == null && filter.objectSizeGreaterThan() == null);
                return coversSources && rule.status() == io.minio.messages.Status.ENABLED
                        && rule.abortIncompleteMultipartUpload() != null
                        && rule.abortIncompleteMultipartUpload().daysAfterInitiation() == 1;
            });
            if (!abortsIncomplete) throw new IllegalStateException("Storage requires one-day incomplete multipart cleanup for sources");
            requirement = "no anonymous bucket policy";
            try {
                String policy = client.getBucketPolicy(GetBucketPolicyArgs.builder().bucket(properties.bucket()).build());
                if (policy != null && !policy.isBlank()) throw new IllegalStateException("Storage bucket must have no anonymous bucket policy");
            } catch (ErrorResponseException exception) {
                if (!exception.errorResponse().code().equals("NoSuchBucketPolicy")) throw exception;
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot verify private storage bucket requirement: " + requirement
                    + "; check configuration, endpoint and application permissions");
        }
    }

    @Override
    public void put(String objectKey, Path file, long size) {
        try (var input = Files.newInputStream(file)) {
            client().putObject(PutObjectArgs.builder().bucket(properties.bucket()).object(objectKey)
                    .stream(input, size, 5L * 1024 * 1024).contentType("video/mp4").build());
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            client().removeObject(RemoveObjectArgs.builder().bucket(properties.bucket()).object(objectKey).build());
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    private MinioClient client() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(properties.operationSeconds());
        var bounded = new OkHttpClient(http.newBuilder()) {
            @Override
            public Call newCall(Request request) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new IllegalStateException("Storage operation deadline exceeded");
                var call = super.newCall(request);
                call.timeout().timeout(remaining, TimeUnit.NANOSECONDS);
                return call;
            }
        };
        return MinioClient.builder().endpoint(properties.endpoint()).credentials(properties.accessKey(), properties.secretKey())
                .httpClient(bounded).build();
    }

    @Override
    public void close() {
        http.dispatcher().executorService().shutdown();
        http.connectionPool().evictAll();
    }
}
