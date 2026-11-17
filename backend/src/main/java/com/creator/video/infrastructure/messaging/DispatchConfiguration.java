package com.creator.video.infrastructure.messaging;

import com.creator.video.application.processing.*;
import com.creator.video.domain.DispatchRepository;
import java.time.Clock;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ConditionalOnProperty(name = "creator.processing.dispatch.enabled", havingValue = "true")
@EnableConfigurationProperties(DispatchProperties.class)
@Import(RabbitMessagingConfiguration.class)
public class DispatchConfiguration {
    @Bean
    DispatchTransactions dispatchTransactions(DispatchRepository repository, DispatchProperties properties, Clock clock) {
        return new DispatchTransactions(repository, properties, clock);
    }
    @Bean
    DispatchService dispatchService(DispatchRepository repository, DispatchTransactions transactions,
            TaskPublisher publisher, DispatchProperties properties, Clock clock) {
        return new DispatchService(repository, transactions, publisher, properties, clock);
    }
    @Bean
    DispatchScheduler processingDispatchScheduler(DispatchService service, DispatchProperties properties) {
        return new DispatchScheduler(service, properties);
    }
    @Bean
    ApplicationRunner startProcessingDispatch(DispatchScheduler scheduler) {
        return arguments -> scheduler.start();
    }
}
