package com.creator.video.application.deletion;

import com.creator.video.application.ObjectStorage;
import com.creator.video.domain.DeletionRepository;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "creator.upload.enabled", havingValue = "true")
public class VideoDeletionConfiguration {
    @Bean
    DeletionTransactions deletionTransactions(DeletionRepository repository, Clock clock) {
        return new DeletionTransactions(repository, clock);
    }

    @Bean
    VideoDeletionMaintenance videoDeletionMaintenance(DeletionRepository repository,
            DeletionTransactions transactions, ObjectStorage storage, Clock clock) {
        return new VideoDeletionMaintenance(repository, transactions, storage, clock);
    }
}
