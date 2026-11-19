package com.creator.video.infrastructure.media;

import com.creator.video.application.MediaSigner;
import com.creator.video.infrastructure.upload.CosObjectStorage;
import com.creator.video.infrastructure.upload.MinioObjectStorage;
import com.creator.video.infrastructure.upload.StorageProperties;
import java.time.Clock;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;

@Configuration
@Profile("!worker")
@ConditionalOnProperty(name = "creator.media.enabled", havingValue = "true")
@EnableConfigurationProperties({MediaProperties.class, StorageProperties.class})
public class MediaConfiguration {
    @Bean
    @ConditionalOnProperty(name = "creator.storage.provider", havingValue = "minio", matchIfMissing = true)
    MediaSigner minioMediaSigner(StorageProperties storage, MediaProperties properties, Clock clock) {
        return new MinioMediaSigner(storage, properties, clock);
    }

    @Bean
    @ConditionalOnProperty(name = "creator.storage.provider", havingValue = "cos")
    MediaSigner cosMediaSigner(StorageProperties storage, MediaProperties properties, Clock clock) {
        return new CosMediaSigner(storage, properties, clock);
    }

    @Bean
    @ConditionalOnProperty(name = "creator.storage.provider", havingValue = "minio", matchIfMissing = true)
    ApplicationRunner verifyMinioMediaBucket(StorageProperties storage) {
        return arguments -> {
            try (var verifier = new MinioObjectStorage(storage)) { verifier.verifyPrivateBucket(); }
        };
    }

    @Bean
    @ConditionalOnProperty(name = "creator.storage.provider", havingValue = "cos")
    ApplicationRunner verifyCosMediaBucket(StorageProperties storage) {
        return arguments -> {
            try (var verifier = new CosObjectStorage(storage)) { verifier.verifyPrivateBucket(); }
        };
    }
}
