package com.creator.video.infrastructure.messaging;

import com.creator.video.application.processing.DispatchProperties;
import org.springframework.amqp.core.*;

public final class ProcessingTopology {
    public static final String EXCHANGE = "creator.processing";
    public static final String QUEUE = "creator.processing.jobs";
    public static final String ROUTING_KEY = "video.process";
    public static final String DEAD_EXCHANGE = "creator.processing.dead";
    public static final String DEAD_QUEUE = "creator.processing.dead.jobs";
    public static final String DEAD_ROUTING_KEY = "video.process.dead";
    public static final int MAX_QUEUE_BYTES = 1048576;

    private ProcessingTopology() { }

    public static Declarables declarations(DispatchProperties properties) {
        var exchange = new DirectExchange(EXCHANGE, true, false);
        var deadExchange = new DirectExchange(DEAD_EXCHANGE, true, false);
        var queue = QueueBuilder.durable(QUEUE).withArgument("x-max-length", properties.maxQueueMessages())
                .withArgument("x-max-length-bytes", MAX_QUEUE_BYTES).withArgument("x-overflow", "reject-publish")
                .withArgument("x-message-ttl", properties.messageTtlSeconds() * 1000)
                .withArgument("x-dead-letter-exchange", DEAD_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DEAD_ROUTING_KEY).build();
        var deadQueue = QueueBuilder.durable(DEAD_QUEUE).withArgument("x-max-length", properties.maxQueueMessages())
                .withArgument("x-max-length-bytes", MAX_QUEUE_BYTES).withArgument("x-message-ttl", 86400000).build();
        return new Declarables(exchange, deadExchange, queue, deadQueue,
                BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY),
                BindingBuilder.bind(deadQueue).to(deadExchange).with(DEAD_ROUTING_KEY));
    }
}
