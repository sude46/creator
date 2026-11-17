package com.creator.video;

import com.creator.video.application.processing.ProcessingWorker;
import com.creator.video.domain.ProcessingMessage;
import com.creator.video.infrastructure.messaging.ProcessingConsumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.junit.jupiter.api.Assertions.*;

class ProcessingConsumerTest {
    @Test
    void messageSchemaRejectsCoercionOverflowExtraDuplicateAndTrailingData() {
        var worker = new TestWorker();
        var consumer = new ProcessingConsumer(worker, new ObjectMapper());
        String valid = "{\"schemaVersion\":1,\"taskId\":2,\"videoId\":3,\"generation\":1}";
        consumer.onMessage(message(valid));
        assertEquals(new ProcessingMessage(1, 2, 3, 1), worker.last);
        worker.last = null;
        for (String invalid : List.of("null", "[]", "{}", valid + "{}", valid.replace("2", "2.5"),
                valid.replace("2", "\"2\""), valid.replace("2", "9223372036854775808"), valid.replace("2", "-2"),
                valid.replace("\"schemaVersion\":1", "\"schemaVersion\":2"), valid.replace("\"taskId\":2", "\"taskId\":2,\"taskId\":4"),
                valid.replace("\"taskId\":2", "\"taskId\":2,\"extra\":0"), " ".repeat(1025))) {
            assertThrows(AmqpRejectAndDontRequeueException.class, () -> consumer.onMessage(message(invalid)), invalid);
        }
        assertNull(worker.last);
    }

    @Test
    void databaseFailureUsesDeadLetterAndDurableRecoveryInsteadOfImmediateRequeueLoop() {
        var worker = new TestWorker();
        worker.fail = true;
        var consumer = new ProcessingConsumer(worker, new ObjectMapper());
        assertThrows(AmqpRejectAndDontRequeueException.class,
                () -> consumer.onMessage(message("{\"schemaVersion\":1,\"taskId\":2,\"videoId\":3,\"generation\":1}")));
    }

    private Message message(String body) { return new Message(body.getBytes(StandardCharsets.UTF_8), new MessageProperties()); }
    static class TestWorker extends ProcessingWorker {
        ProcessingMessage last;
        boolean fail;
        TestWorker() { super(null, null, null, null, null); }
        @Override
        public Decision consume(ProcessingMessage message) {
            if (fail) throw new DataAccessResourceFailureException("injected outage");
            last = message;
            return Decision.ACK;
        }
    }
}
