package com.creator.video.infrastructure.messaging;

import com.creator.video.application.processing.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.impl.nio.NioParams;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.amqp.ConnectionFactoryCustomizer;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.support.RetryTemplate;

@ConditionalOnProperty(name = "creator.processing.dispatch.provider", havingValue = "rabbitmq", matchIfMissing = true)
public class RabbitMessagingConfiguration {
    @Bean
    ConnectionFactoryCustomizer boundedRabbitConnection(RabbitProperties properties) {
        if (properties.getHost() == null || properties.getHost().isBlank()
                || properties.getUsername() == null || properties.getUsername().isBlank()
                || properties.getPassword() == null || properties.getPassword().isBlank()
                || properties.getPort() < 1 || properties.getPort() > 65535) {
            throw new IllegalStateException("Configure RabbitMQ host, port and application credentials before enabling dispatch");
        }
        return factory -> {
            factory.setConnectionTimeout(5000);
            factory.setHandshakeTimeout(5000);
            factory.setChannelRpcTimeout(5000);
            factory.setRequestedHeartbeat(30);
            factory.setRequestedChannelMax(16);
            factory.setAutomaticRecoveryEnabled(false);
            factory.setNioParams(new NioParams().setNbIoThreads(1).setWriteQueueCapacity(64).setWriteEnqueuingTimeoutInMs(5000));
            factory.useNio();
        };
    }
    @Bean
    Declarables processingTopology(DispatchProperties properties) { return ProcessingTopology.declarations(properties); }
    @Bean
    RabbitAdmin processingRabbitAdmin(ConnectionFactory factory) {
        var publisherFactory = factory.getPublisherConnectionFactory();
        var admin = new RabbitAdmin(publisherFactory == null ? factory : publisherFactory);
        admin.setRetryTemplate(RetryTemplate.builder().maxAttempts(1).build());
        return admin;
    }
    @Bean
    RabbitTemplate processingRabbitTemplate(ConnectionFactory factory) {
        var template = new RabbitTemplate(factory);
        template.setMandatory(true);
        template.setUsePublisherConnection(true);
        return template;
    }
    @Bean
    TaskPublisher taskPublisher(@Qualifier("processingRabbitTemplate") RabbitTemplate template,
            ObjectMapper json, DispatchProperties properties) {
        return new RabbitTaskPublisher(template, json, properties);
    }
}
