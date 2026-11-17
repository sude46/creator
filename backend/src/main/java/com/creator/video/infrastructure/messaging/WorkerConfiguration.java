package com.creator.video.infrastructure.messaging;

import com.creator.video.application.processing.*;
import com.creator.video.domain.ExecutionRepository;
import com.creator.video.infrastructure.media.*;
import com.creator.video.infrastructure.upload.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.*;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration
@Profile("worker")
@EnableConfigurationProperties({WorkerProperties.class, DispatchProperties.class})
@Import({RabbitMessagingConfiguration.class, WorkerConfiguration.WorkerStorageConfiguration.class})
public class WorkerConfiguration {
    @Bean
    ExecutionTransactions executionTransactions(ExecutionRepository repository, WorkerProperties properties, Clock clock) {
        return new ExecutionTransactions(repository, properties, clock);
    }
    @Bean
    ProcessingWorkspace processingWorkspace(WorkerProperties properties) throws IOException { return new ProcessingWorkspace(properties); }
    @Bean
    @ConditionalOnProperty(name = "creator.processing.worker.media-provider", havingValue = "ffmpeg", matchIfMissing = true)
    MediaProcessor mediaProcessor(WorkerProperties properties, ObjectMapper json) {
        return new FfmpegMediaProcessor(properties, json, new BoundedMediaProcess());
    }
    @Bean
    ProcessingWorker processingWorker(ExecutionTransactions transactions, ProcessingStorage storage,
            MediaProcessor processor, ProcessingWorkspace workspace, WorkerProperties properties) {
        return new ProcessingWorker(transactions, storage, processor, workspace, properties);
    }
    @Bean
    ExecutionMaintenance executionMaintenance(ExecutionRepository repository, ExecutionTransactions transactions,
            ProcessingStorage storage, ProcessingWorkspace workspace, Clock clock) {
        return new ExecutionMaintenance(repository, transactions, storage, workspace, clock);
    }
    @Bean
    RabbitAdmin workerRabbitAdmin(ConnectionFactory factory) {
        var admin = new RabbitAdmin(factory);
        admin.setRetryTemplate(RetryTemplate.builder().maxAttempts(1).build());
        return admin;
    }
    @Bean
    WorkerRuntime workerRuntime(ConnectionFactory factory, ProcessingWorker worker, ObjectMapper json,
            ExecutionMaintenance maintenance) {
        var container = new SimpleMessageListenerContainer(factory);
        container.setQueueNames(ProcessingTopology.QUEUE);
        container.setConcurrentConsumers(1);
        container.setMaxConcurrentConsumers(1);
        container.setPrefetchCount(1);
        container.setAcknowledgeMode(AcknowledgeMode.AUTO);
        container.setDefaultRequeueRejected(false);
        container.setAutoStartup(false);
        container.setMissingQueuesFatal(false);
        container.setRecoveryInterval(5000);
        container.setShutdownTimeout(5000);
        container.setMessageListener(new ProcessingConsumer(worker, json));
        container.afterPropertiesSet();
        return new WorkerRuntime(container, maintenance, worker);
    }
    @Bean
    ApplicationRunner startWorker(WorkerRuntime runtime, ProcessingStorage storage, MediaProcessor media, Environment environment) {
        return arguments -> {
            if (!environment.getProperty("spring.main.web-application-type", "").equalsIgnoreCase(WebApplicationType.NONE.name())
                    || environment.getProperty("creator.upload.enabled", Boolean.class, false)
                    || environment.getProperty("creator.processing.dispatch.enabled", Boolean.class, false)) {
                throw new IllegalStateException("Worker requires non-Web runtime with API upload and dispatch disabled");
            }
            storage.verify();
            media.verify();
            runtime.start();
        };
    }

    public static class WorkerRuntime implements AutoCloseable {
        private final SimpleMessageListenerContainer container;
        private final ExecutionMaintenance maintenance;
        private final ProcessingWorker worker;
        private ScheduledExecutorService scheduler;
        WorkerRuntime(SimpleMessageListenerContainer container, ExecutionMaintenance maintenance, ProcessingWorker worker) {
            this.container = container;
            this.maintenance = maintenance;
            this.worker = worker;
        }
        public void start() {
            scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("execution-maintenance").factory());
            scheduler.scheduleWithFixedDelay(maintenance, 5, 5, TimeUnit.SECONDS);
            container.start();
        }
        @Override
        public void close() {
            worker.close();
            container.stop();
            if (scheduler != null) scheduler.shutdownNow();
            container.destroy();
        }
    }

    @ConditionalOnProperty(name = "creator.storage.provider", havingValue = "minio", matchIfMissing = true)
    @EnableConfigurationProperties(StorageProperties.class)
    static class WorkerStorageConfiguration {
        @Bean
        ProcessingStorage processingStorage(StorageProperties properties) { return new MinioObjectStorage(properties); }
    }
}
