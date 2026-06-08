package com.example.notify.interfaces.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.notify.domain.exception.NotificationExceptionRecord;
import com.example.notify.domain.exception.NotificationExceptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.support.Acknowledgment;

class NotificationDltConsumerTest {

    private NotificationExceptions notificationExceptions;
    private Acknowledgment ack;
    private ObjectMapper objectMapper;
    private NotificationDltConsumer consumer;

    @BeforeEach
    void setUp() {
        notificationExceptions = mock(NotificationExceptions.class);
        ack = mock(Acknowledgment.class);
        objectMapper = new ObjectMapper();
        consumer = new NotificationDltConsumer(notificationExceptions, objectMapper);
    }

    @Test
    void consumeDeserializesDltMessageAndPersistsException() throws Exception {
        Map<String, Object> eventMap = new LinkedHashMap<>();
        eventMap.put("notificationId", "notif-dlt-1");
        eventMap.put("strategyId", "strat-dlt-1");
        eventMap.put("customerId", "cust-dlt-1");
        eventMap.put("eventId", "evt-dlt-1");
        eventMap.put("userId", "user-1");
        eventMap.put("eventType", "PRODUCT_VIEW");
        eventMap.put("triggeredAt", Instant.now().toString());
        eventMap.put("window", "PT30S");
        eventMap.put("threshold", 5);
        eventMap.put("currentCount", 7);
        eventMap.put("dedupeKey", "dedupe-1");
        String json = objectMapper.writeValueAsString(eventMap);

        ConsumerRecord<String, String> record = new ConsumerRecord<>("notification-events-dlt", 0, 0, "cust-dlt-1", json);

        consumer.consume(record, ack);

        verify(notificationExceptions).add(any(NotificationExceptionRecord.class));
        verify(ack).acknowledge();
    }

    @Test
    void consumeReadsRetryCountFromDeliveryAttemptHeader() throws Exception {
        Map<String, Object> eventMap = new LinkedHashMap<>();
        eventMap.put("notificationId", "notif-header-1");
        eventMap.put("strategyId", "strat-header-1");
        eventMap.put("customerId", "cust-header-1");
        eventMap.put("eventId", "evt-header-1");
        eventMap.put("eventType", "PRODUCT_VIEW");
        eventMap.put("triggeredAt", Instant.now().toString());
        eventMap.put("window", "PT30S");
        eventMap.put("threshold", 5);
        eventMap.put("currentCount", 7);
        eventMap.put("dedupeKey", "dedupe-header");
        String json = objectMapper.writeValueAsString(eventMap);

        ConsumerRecord<String, String> record = new ConsumerRecord<>("notification-events-dlt", 0, 0, "cust-header-1", json);
        record.headers().add(new RecordHeader("kafka_delivery-attempt", "5".getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("kafka_original-topic", "notification-events".getBytes(StandardCharsets.UTF_8)));

        consumer.consume(record, ack);

        ArgumentCaptor<NotificationExceptionRecord> captor = ArgumentCaptor.forClass(NotificationExceptionRecord.class);
        verify(notificationExceptions).add(captor.capture());

        NotificationExceptionRecord captured = captor.getValue();
        assertThat(captured.retryCount()).isEqualTo(5);
        assertThat(captured.status()).isEqualTo("RETRY_EXHAUSTED");
        assertThat(captured.failureReason()).isEqualTo("exhausted retries from topic notification-events");
        verify(ack).acknowledge();
    }

    @Test
    void consumeFallsBackToDefaultWhenNoHeaders() throws Exception {
        Map<String, Object> eventMap = new LinkedHashMap<>();
        eventMap.put("notificationId", "notif-noheader-1");
        eventMap.put("strategyId", "strat-noheader-1");
        eventMap.put("customerId", "cust-noheader-1");
        eventMap.put("eventId", "evt-noheader-1");
        eventMap.put("eventType", "PRODUCT_VIEW");
        eventMap.put("triggeredAt", Instant.now().toString());
        eventMap.put("window", "PT30S");
        eventMap.put("threshold", 5);
        eventMap.put("currentCount", 7);
        eventMap.put("dedupeKey", "dedupe-noheader");
        String json = objectMapper.writeValueAsString(eventMap);

        ConsumerRecord<String, String> record = new ConsumerRecord<>("notification-events-dlt", 0, 0, "cust-noheader-1", json);

        consumer.consume(record, ack);

        ArgumentCaptor<NotificationExceptionRecord> captor = ArgumentCaptor.forClass(NotificationExceptionRecord.class);
        verify(notificationExceptions).add(captor.capture());

        NotificationExceptionRecord captured = captor.getValue();
        assertThat(captured.retryCount()).isEqualTo(1);
        assertThat(captured.failureReason()).isEqualTo("exhausted retries");
        verify(ack).acknowledge();
    }

    @Test
    void consumeDoesNotAckOnInvalidJson() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("notification-events-dlt", 0, 0, "key", "not-json");

        assertThrows(RuntimeException.class, () -> consumer.consume(record, ack));
        verify(ack, never()).acknowledge();
        verify(notificationExceptions, never()).add(any(NotificationExceptionRecord.class));
    }

    @Test
    void consumeDoesNotAckOnPersistenceFailure() throws Exception {
        Map<String, Object> eventMap = new LinkedHashMap<>();
        eventMap.put("notificationId", "notif-dlt-fail");
        eventMap.put("strategyId", "strat-dlt-fail");
        eventMap.put("customerId", "cust-dlt-fail");
        eventMap.put("eventId", "evt-dlt-fail");
        eventMap.put("userId", "user-1");
        eventMap.put("eventType", "PRODUCT_VIEW");
        eventMap.put("triggeredAt", Instant.now().toString());
        eventMap.put("window", "PT30S");
        eventMap.put("threshold", 5);
        eventMap.put("currentCount", 7);
        eventMap.put("dedupeKey", "dedupe-fail");
        String json = objectMapper.writeValueAsString(eventMap);

        org.mockito.Mockito.doThrow(new RuntimeException("db error"))
            .when(notificationExceptions).add(any(NotificationExceptionRecord.class));

        ConsumerRecord<String, String> record = new ConsumerRecord<>("notification-events-dlt", 0, 0, "key", json);

        assertThrows(RuntimeException.class, () -> consumer.consume(record, ack));
        verify(ack, never()).acknowledge();
    }

}
