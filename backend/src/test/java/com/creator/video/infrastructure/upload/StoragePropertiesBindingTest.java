package com.creator.video.infrastructure.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;

class StoragePropertiesBindingTest {
    @Test
    void bindsCanonicalRecordConstructorWithCleanupMode() {
        try (var context = new AnnotationConfigApplicationContext()) {
            TestPropertyValues.of(
                    "creator.storage.endpoint=http://127.0.0.1:9000",
                    "creator.storage.access-key=test-access",
                    "creator.storage.secret-key=test-secret-12345",
                    "creator.storage.bucket=creator-private",
                    "creator.storage.operation-seconds=5",
                    "creator.storage.incomplete-upload-cleanup=MINIO_DEFAULT").applyTo(context);
            ConfigurationPropertiesBindingPostProcessor.register(context);
            context.register(Config.class);
            context.refresh();
            assertEquals(StorageProperties.IncompleteUploadCleanup.MINIO_DEFAULT,
                    context.getBean(StorageProperties.class).incompleteUploadCleanup());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(StorageProperties.class)
    static class Config {
    }
}
