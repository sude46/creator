package com.creator.video.infrastructure.upload;

import com.creator.audit.application.AuditLog;
import com.creator.video.application.*;
import com.creator.video.domain.UploadRepository;
import com.creator.video.web.UploadAdmissionFilter;
import com.creator.identity.infrastructure.security.ActiveAccountFilter;
import com.creator.identity.infrastructure.security.SecurityResponseWriter;
import java.io.IOException;
import java.time.Clock;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

@Configuration
@ConditionalOnProperty(name = "creator.upload.enabled", havingValue = "true")
@EnableConfigurationProperties(UploadProperties.class)
@EnableScheduling
@Import(UploadConfiguration.MinioConfiguration.class)
public class UploadConfiguration {
    @Bean
    UploadStaging uploadStaging(UploadProperties properties) throws IOException { return new UploadStaging(properties); }
    @Bean
    UploadAdmission uploadAdmission(UploadProperties properties, UploadStaging staging) { return new UploadAdmission(properties, staging); }
    @Bean
    Customizer<HttpSecurity> uploadSecurityCustomizer(UploadAdmission admission, SecurityResponseWriter writer) {
        return http -> http.addFilterAfter(new UploadAdmissionFilter(admission, writer), ActiveAccountFilter.class);
    }
    @Bean
    UploadTransactions uploadTransactions(UploadRepository repository, AuditLog audit, UploadProperties properties, Clock clock) {
        return new UploadTransactions(repository, audit, properties, clock);
    }
    @Bean
    UploadService uploadService(UploadTransactions transactions, UploadStaging staging, ObjectStorage storage) {
        return new UploadService(transactions, staging, storage);
    }
    @Bean
    UploadMaintenance uploadMaintenance(UploadRepository repository, UploadTransactions transactions, ObjectStorage storage,
            UploadStaging staging, Clock clock) {
        return new UploadMaintenance(repository, transactions, storage, staging, clock);
    }
    @Bean
    ApplicationRunner verifyStorage(ObjectStorage storage) { return arguments -> storage.verifyPrivateBucket(); }
    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> uploadReadTimeout() {
        return factory -> factory.addConnectorCustomizers(connector -> {
            connector.setProperty("disableUploadTimeout", "false");
            connector.setProperty("connectionUploadTimeout", "15000");
        });
    }

    @ConditionalOnProperty(name = "creator.storage.provider", havingValue = "minio", matchIfMissing = true)
    @EnableConfigurationProperties(StorageProperties.class)
    static class MinioConfiguration {
        @Bean
        ObjectStorage objectStorage(StorageProperties properties) { return new MinioObjectStorage(properties); }
    }
}
