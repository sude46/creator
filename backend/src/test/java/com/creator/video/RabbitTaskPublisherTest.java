package com.creator.video;

import com.creator.video.application.processing.DispatchProperties;
import com.creator.video.domain.*;
import com.creator.video.infrastructure.messaging.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.*;

class RabbitTaskPublisherTest {
    @Test
    void persistentMinimalMessageUsesDispatchTokenAndStableIdentity() throws Exception {
        var claim = claim();
        var observed = new AtomicBoolean();
        var template = new RabbitTemplate() {
            @Override public void send(String exchange, String key, Message message, CorrelationData correlation) {
                assertEquals(ProcessingTopology.EXCHANGE, exchange);
                assertEquals(ProcessingTopology.ROUTING_KEY, key);
                assertEquals(claim.dispatchToken(), correlation.getId());
                assertEquals(MessageDeliveryMode.PERSISTENT, message.getMessageProperties().getDeliveryMode());
                assertEquals("1:1", message.getMessageProperties().getMessageId());
                try {
                    var body = new ObjectMapper().readTree(message.getBody());
                    assertEquals(4, body.size());
                    assertEquals(1, body.get("schemaVersion").asInt());
                    assertEquals(1, body.get("taskId").asLong());
                    assertEquals(2, body.get("videoId").asLong());
                    assertEquals(1, body.get("generation").asLong());
                } catch (Exception exception) { throw new AssertionError(exception); }
                observed.set(true);
                correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            }
        };
        assertEquals(PublicationResult.CONFIRMED, publisher(template).publish(claim));
        assertTrue(observed.get());
    }

    @Test
    void returnedMessageFailsEvenWhenBrokerAcknowledgesPublish() {
        var template = new RabbitTemplate() {
            @Override public void send(String exchange, String key, Message message, CorrelationData correlation) {
                correlation.setReturned(new ReturnedMessage(message, 312, "NO_ROUTE", exchange, key));
                correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            }
        };
        assertEquals(PublicationResult.UNROUTABLE, publisher(template).publish(claim()));
    }

    @Test
    void nackTimeoutAndConnectionFailuresHaveExplicitOutcomes() {
        var nack = new RabbitTemplate() {
            @Override public void send(String exchange, String key, Message message, CorrelationData correlation) {
                correlation.getFuture().complete(new CorrelationData.Confirm(false, "rejected"));
            }
        };
        assertEquals(PublicationResult.BROKER_NACK, publisher(nack).publish(claim()));
        var unavailable = new RabbitTemplate() {
            @Override public void send(String exchange, String key, Message message, CorrelationData correlation) {
                throw new AmqpException("Injected unavailable broker");
            }
        };
        assertEquals(PublicationResult.BROKER_UNAVAILABLE, publisher(unavailable).publish(claim()));
        var timeout = new RabbitTemplate() {
            @Override public void send(String exchange, String key, Message message, CorrelationData correlation) { }
        };
        assertEquals(PublicationResult.CONFIRM_TIMEOUT, publisher(timeout).publish(claim()));
    }

    @Test
    void shutdownInterruptIsPreserved() {
        var template = new RabbitTemplate() {
            @Override public void send(String exchange, String key, Message message, CorrelationData correlation) { }
        };
        Thread.currentThread().interrupt();
        try {
            assertEquals(PublicationResult.INTERRUPTED, publisher(template).publish(claim()));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }

    static DispatchProperties properties() { return new DispatchProperties(2, 1, 45, 600, 300, 1000, 3600000, 300); }
    private RabbitTaskPublisher publisher(RabbitTemplate template) { return new RabbitTaskPublisher(template, new ObjectMapper(), properties()); }
    private DispatchClaim claim() { return new DispatchClaim(1L, 2L, 1L, UUID.randomUUID().toString(), 1L); }
}
