package com.creator.video;

import com.creator.video.application.processing.TaskPublisher;
import com.creator.video.domain.DispatchClaim;
import com.creator.video.domain.PublicationResult;
import com.creator.video.infrastructure.messaging.ProcessingTopology;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "CREATOR_RABBIT_IT_PORT", matches = "[0-9]+")
@SpringBootTest(properties = {"creator.processing.dispatch.enabled=true",
        "creator.processing.dispatch.scan-milliseconds=3600000", "creator.processing.dispatch.message-ttl-seconds=30",
        "creator.processing.dispatch.max-queue-messages=2", "spring.rabbitmq.port=${CREATOR_RABBIT_IT_PORT}",
        "spring.rabbitmq.username=creator_test", "spring.rabbitmq.password=integration-only-password",
        "spring.rabbitmq.virtual-host=creator_config_it"})
@ActiveProfiles("test")
class RabbitConfigurationIntegrationTest {
    @Autowired TaskPublisher publisher;
    @Autowired ConnectionFactory factory;
    @Autowired RabbitAdmin admin;

    @Test
    void firstPublicationDeclaresTopologyOnDedicatedPublisherConnection() {
        assertTrue(factory.isPublisherConfirms());
        assertTrue(factory.isPublisherReturns());
        assertNotNull(factory.getPublisherConnectionFactory());
        assertTrue(factory.getPublisherConnectionFactory().isPublisherConfirms());
        assertTrue(factory.getPublisherConnectionFactory().isPublisherReturns());
        var claim = new DispatchClaim(1L, 2L, 1L, UUID.randomUUID().toString(), 1L);
        assertEquals(PublicationResult.CONFIRMED, publisher.publish(claim));
        assertNotNull(admin.getQueueProperties(ProcessingTopology.QUEUE));
        var reader = new RabbitTemplate(factory.getPublisherConnectionFactory());
        assertNotNull(reader.receive(ProcessingTopology.QUEUE, 2000));
    }
}
