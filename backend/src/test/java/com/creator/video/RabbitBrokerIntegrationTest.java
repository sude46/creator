package com.creator.video;

import com.creator.video.application.processing.DispatchProperties;
import com.creator.video.domain.*;
import com.creator.video.infrastructure.messaging.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "CREATOR_RABBIT_IT_PORT", matches = "[0-9]+")
class RabbitBrokerIntegrationTest {
    CachingConnectionFactory connection;
    RabbitAdmin admin;
    RabbitTemplate template;
    RabbitTaskPublisher publisher;
    Queue queue;
    Queue deadQueue;
    Binding binding;

    @BeforeEach
    void connect() {
        connection = new CachingConnectionFactory("127.0.0.1", Integer.parseInt(System.getenv("CREATOR_RABBIT_IT_PORT")));
        connection.setUsername("creator_test");
        connection.setPassword("integration-only-password");
        connection.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        connection.setPublisherReturns(true);
        connection.setConnectionTimeout(5000);
        template = new RabbitTemplate(connection);
        template.setMandatory(true);
        admin = new RabbitAdmin(connection);
        var properties = new DispatchProperties(2, 3, 45, 600, 1, 1, 3600000, 300);
        // The dedicated test broker may have queues left by an earlier app/test run.
        // Recreate them so queue arguments are deterministic.
        admin.deleteQueue(ProcessingTopology.QUEUE);
        admin.deleteQueue(ProcessingTopology.DEAD_QUEUE);
        for (var declaration : ProcessingTopology.declarations(properties).getDeclarables()) {
            if (declaration instanceof Exchange exchange) admin.declareExchange(exchange);
            else if (declaration instanceof Queue declaredQueue) {
                admin.declareQueue(declaredQueue);
                if (declaredQueue.getName().equals(ProcessingTopology.QUEUE)) queue = declaredQueue;
                else deadQueue = declaredQueue;
            } else if (declaration instanceof Binding declaredBinding) {
                admin.declareBinding(declaredBinding);
                if (declaredBinding.getDestination().equals(ProcessingTopology.QUEUE)) binding = declaredBinding;
            }
        }
        admin.purgeQueue(ProcessingTopology.QUEUE, false);
        admin.purgeQueue(ProcessingTopology.DEAD_QUEUE, false);
        publisher = new RabbitTaskPublisher(template, new ObjectMapper(), properties);
    }

    @AfterEach
    void disconnect() {
        try {
            if (queue != null) admin.deleteQueue(queue.getName());
            if (deadQueue != null) admin.deleteQueue(deadQueue.getName());
        } finally { if (connection != null) connection.destroy(); }
    }

    @Test
    void brokerConfirmsAndDeliversPersistentMessage() throws Exception {
        var claim = claim();
        assertEquals(PublicationResult.CONFIRMED, publisher.publish(claim));
        var received = template.receive(ProcessingTopology.QUEUE, 2000);
        assertNotNull(received);
        assertEquals(MessageDeliveryMode.PERSISTENT, received.getMessageProperties().getReceivedDeliveryMode());
        assertEquals("1:1", received.getMessageProperties().getMessageId());
        assertEquals(4, new ObjectMapper().readTree(received.getBody()).size());
    }

    @Test
    void mandatoryReturnIsNotMistakenForSuccessfulConfirm() {
        admin.removeBinding(binding);
        try { assertEquals(PublicationResult.UNROUTABLE, publisher.publish(claim())); }
        finally { admin.declareBinding(binding); }
    }

    @Test
    void fullQueueRejectsPublicationInsteadOfSilentlyDroppingOldWork() {
        assertEquals(PublicationResult.CONFIRMED, publisher.publish(claim()));
        assertEquals(PublicationResult.BROKER_NACK, publisher.publish(claim()));
        assertNotNull(template.receive(ProcessingTopology.QUEUE, 1000));
        assertNull(template.receive(ProcessingTopology.QUEUE));
    }

    @Test
    void expiredMessagesEnterBoundedDeadLetterQueue() {
        assertEquals(1000, queue.getArguments().get("x-message-ttl"));
        assertEquals(ProcessingTopology.DEAD_EXCHANGE, queue.getArguments().get("x-dead-letter-exchange"));
        var expiryQueue = QueueBuilder.nonDurable("creator.processing.expiry.it-" + UUID.randomUUID())
                .autoDelete().withArgument("x-message-ttl", 1000)
                .withArgument("x-dead-letter-exchange", ProcessingTopology.DEAD_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ProcessingTopology.DEAD_ROUTING_KEY).build();
        admin.declareQueue(expiryQueue);
        try {
            template.convertAndSend("", expiryQueue.getName(), "expire");
            var expired = template.receive(ProcessingTopology.DEAD_QUEUE, 4000);
            assertNotNull(expired);
            assertTrue(expired.getMessageProperties().getHeaders().containsKey("x-death"));
            assertNull(template.receive(ProcessingTopology.QUEUE));
        } finally {
            admin.deleteQueue(expiryQueue.getName());
        }
    }

    private DispatchClaim claim() { return new DispatchClaim(1L, 2L, 1L, UUID.randomUUID().toString(), 1L); }
}
