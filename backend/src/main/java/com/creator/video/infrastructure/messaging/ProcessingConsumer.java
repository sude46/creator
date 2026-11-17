package com.creator.video.infrastructure.messaging;

import com.creator.video.application.processing.ProcessingWorker;
import com.creator.video.domain.ProcessingMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.core.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessingConsumer implements MessageListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessingConsumer.class);
    private final ProcessingWorker worker;
    private final ObjectMapper json;

    public ProcessingConsumer(ProcessingWorker worker, ObjectMapper json) {
        this.worker = worker;
        this.json = json;
    }

    @Override
    public void onMessage(Message message) {
        ProcessingMessage body;
        try {
            if (message.getBody().length > 1024) throw new IllegalArgumentException();
            var root = json.reader().with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(message.getBody());
            if (root == null || !root.isObject() || root.size() != 4) throw new IllegalArgumentException();
            for (String field : java.util.List.of("schemaVersion", "taskId", "videoId", "generation")) {
                var value = root.get(field);
                if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) throw new IllegalArgumentException();
            }
            body = new ProcessingMessage(Math.toIntExact(root.get("schemaVersion").longValue()),
                    root.get("taskId").longValue(), root.get("videoId").longValue(), root.get("generation").longValue());
        } catch (Exception exception) {
            throw new AmqpRejectAndDontRequeueException("Invalid processing message");
        }
        try {
            if (worker.consume(body) == ProcessingWorker.Decision.REJECT) {
                throw new AmqpRejectAndDontRequeueException("Unknown processing task or mismatched identity");
            }
        } catch (AmqpRejectAndDontRequeueException exception) { throw exception; }
        catch (RuntimeException exception) {
            LOGGER.warn("Processing delivery deferred [taskId={}, failure={}]", body.taskId(), exception.getClass().getSimpleName());
            throw new AmqpRejectAndDontRequeueException("Processing deferred to durable database recovery");
        }
    }
}
