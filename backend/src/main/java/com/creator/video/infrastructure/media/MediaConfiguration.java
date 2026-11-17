package com.creator.video.infrastructure.media;

import com.creator.video.application.MediaSigner;
import com.creator.video.infrastructure.upload.*;
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
    MediaSigner mediaSigner(StorageProperties storage, MediaProperties properties, Clock clock) {
        return new MinioMediaSigner(storage, properties, clock);
    }
    @Bean
    ApplicationRunner verifyMediaBucket(StorageProperties storage) {
        return arguments -> {
            try (var verifier = new MinioObjectStorage(storage)) { verifier.verifyPrivateBucket(); }
        };
    }
}
