package com.example.notify.interfaces.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import com.example.notify.domain.exception.UserOperationExceptionRecord;
import com.example.notify.domain.exception.UserOperationExceptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

class UserOperationDltConsumerTest {

    private UserOperationExceptions userOperationExceptions;
    private Acknowledgment ack;
    private ObjectMapper objectMapper;
    private UserOperationDltConsumer consumer;

    @BeforeEach
    void setUp() {
        userOperationExceptions = mock(UserOperationExceptions.class);
        ack = mock(Acknowledgment.class);
        objectMapper = new ObjectMapper();
        consumer = new UserOperationDltConsumer(userOperationExceptions, objectMapper);
    }

    @Test
    void consumeDeserializesDltMessageAndPersistsException() throws Exception {
        Map<String, Object> eventMap = new LinkedHashMap<>();
        eventMap.put("eventId", "evt-dlt-1");
        eventMap.put("customerId", "cust-dlt-1");
        eventMap.put("eventType", "PRODUCT_VIEW");
        eventMap.put("userId", "user-1");
        String json = objectMapper.writeValueAsString(eventMap);

        ConsumerRecord<String, String> record = new ConsumerRecord<>("user-operation-events-dlt", 0, 0, "cust-dlt-1", json);

        consumer.consume(record, ack);

        verify(userOperationExceptions).add(any(UserOperationExceptionRecord.class));
        verify(ack).acknowledge();
    }

    @Test
    void consumeDoesNotAckOnInvalidJson() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("user-operation-events-dlt", 0, 0, "key", "not-json");

        consumer.consume(record, ack);

        verify(ack, never()).acknowledge();
        verify(userOperationExceptions, never()).add(any(UserOperationExceptionRecord.class));
    }

    @Test
    void consumeDoesNotAckOnPersistenceFailure() throws Exception {
        Map<String, Object> eventMap = new LinkedHashMap<>();
        eventMap.put("eventId", "evt-dlt-fail");
        eventMap.put("customerId", "cust-dlt-fail");
        eventMap.put("eventType", "PRODUCT_VIEW");
        String json = objectMapper.writeValueAsString(eventMap);

        org.mockito.Mockito.doThrow(new RuntimeException("db error"))
            .when(userOperationExceptions).add(any(UserOperationExceptionRecord.class));

        ConsumerRecord<String, String> record = new ConsumerRecord<>("user-operation-events-dlt", 0, 0, "key", json);

        consumer.consume(record, ack);

        verify(ack, never()).acknowledge();
    }

}
