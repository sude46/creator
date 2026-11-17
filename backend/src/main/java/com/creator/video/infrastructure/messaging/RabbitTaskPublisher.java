package com.creator.video.infrastructure.messaging;

import com.creator.video.application.processing.DispatchProperties;
import com.creator.video.application.processing.TaskPublisher;
import com.creator.video.domain.DispatchClaim;
import com.creator.video.domain.PublicationResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

public class RabbitTaskPublisher implements TaskPublisher {
    private final RabbitTemplate template;
    private final ObjectMapper json;
    private final DispatchProperties properties;

    public RabbitTaskPublisher(RabbitTemplate template, ObjectMapper json, DispatchProperties properties) {
        this.template = template;
        this.json = json;
        this.properties = properties;
    }

    @Override
    public PublicationResult publish(DispatchClaim claim) {
        byte[] body;
        try { body = json.writeValueAsBytes(claim.message()); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot encode processing message", exception); }
        var metadata = new MessageProperties();
        metadata.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        metadata.setContentEncoding("UTF-8");
        metadata.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        metadata.setMessageId(claim.id() + ":" + claim.generation());
        var correlation = new CorrelationData(claim.dispatchToken());
        try {
            template.send(ProcessingTopology.EXCHANGE, ProcessingTopology.ROUTING_KEY, new Message(body, metadata), correlation);
            var confirm = correlation.getFuture().get(properties.confirmSeconds(), TimeUnit.SECONDS);
            if (correlation.getReturned() != null) return PublicationResult.UNROUTABLE;
            return confirm.isAck() ? PublicationResult.CONFIRMED : PublicationResult.BROKER_NACK;
        } catch (TimeoutException exception) {
            return PublicationResult.CONFIRM_TIMEOUT;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return PublicationResult.INTERRUPTED;
        } catch (AmqpException | ExecutionException exception) {
            return PublicationResult.BROKER_UNAVAILABLE;
        }
    }
}
