package com.example.notify.interfaces.kafka;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.notify.application.notification.PersistNotification;
import com.example.notify.domain.notification.NotificationEvent;
import com.example.notify.domain.notification.NotificationRecord;
import com.example.notify.domain.notification.NotificationRecords;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

class NotificationEventsConsumerTest {

    private NotificationRecords notificationRecords;
    private PersistNotification persistNotification;
    private Acknowledgment ack;
    private ObjectMapper objectMapper;
    private NotificationEventsConsumer consumer;

    @BeforeEach
    void setUp() {
        notificationRecords = mock(NotificationRecords.class);
        persistNotification = new PersistNotification(notificationRecords);
        ack = mock(Acknowledgment.class);
        objectMapper = new ObjectMapper();
        consumer = new NotificationEventsConsumer(persistNotification, objectMapper);
    }

    private static Map<String, Object> notificationEventMap(
            String notificationId, String strategyId, String customerId, String userId,
            String eventId, String eventType, String window, int threshold,
            int currentCount, String dedupeKey) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("notificationId", notificationId);
        map.put("strategyId", strategyId);
        map.put("customerId", customerId);
        map.put("userId", userId);
        map.put("eventId", eventId);
        map.put("eventType", eventType);
        map.put("triggeredAt", Instant.now().toString());
        map.put("window", window);
        map.put("threshold", threshold);
        map.put("currentCount", currentCount);
        map.put("dedupeKey", dedupeKey);
        return map;
    }

    @Test
    void consumeDeserializesAndPersistsNotification() throws Exception {
        String json = objectMapper.writeValueAsString(notificationEventMap(
            "notif-1", "strat-1", "cust-1", "user-1",
            "evt-1", "PRODUCT_VIEW", "PT30S", 5, 7, "dedupe-1"
        ));
        ConsumerRecord<String, String> record = new ConsumerRecord<>("notification-events", 0, 0, "cust-1", json);

        consumer.consume(record, ack);

        verify(notificationRecords).addIfAbsent(any(NotificationRecord.class));
        verify(ack).acknowledge();
    }

    @Test
    void consumeAcksOnSuccess() throws Exception {
        String json = objectMapper.writeValueAsString(notificationEventMap(
            "notif-ack", "strat-ack", "cust-ack", "user-ack",
            "evt-ack", "ORDER_CREATED", "PT1M", 3, 4, "dedupe-ack"
        ));
        ConsumerRecord<String, String> record = new ConsumerRecord<>("notification-events", 0, 0, "cust-ack", json);

        consumer.consume(record, ack);

        verify(ack).acknowledge();
    }

    @Test
    void consumeDoesNotAckOnPersistenceFailure() throws Exception {
        doThrow(new RuntimeException("db error")).when(notificationRecords).addIfAbsent(any(NotificationRecord.class));

        String json = objectMapper.writeValueAsString(notificationEventMap(
            "notif-fail", "strat-fail", "cust-fail", "user-fail",
            "evt-fail", "PRODUCT_VIEW", "PT30S", 5, 7, "dedupe-fail"
        ));
        ConsumerRecord<String, String> record = new ConsumerRecord<>("notification-events", 0, 0, "cust-fail", json);

        assertThrows(RuntimeException.class, () -> consumer.consume(record, ack));
        verify(ack, never()).acknowledge();
    }

    @Test
    void consumeDoesNotAckOnInvalidJson() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("notification-events", 0, 0, "key", "not-json");

        assertThrows(RuntimeException.class, () -> consumer.consume(record, ack));
        verify(ack, never()).acknowledge();
        verify(notificationRecords, never()).addIfAbsent(any(NotificationRecord.class));
    }

    @Test
    void consumeDoesNotAckOnMissingFields() throws Exception {
        Map<String, Object> incomplete = Map.of("notificationId", "notif-missing");
        String json = objectMapper.writeValueAsString(incomplete);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("notification-events", 0, 0, "key", json);

        assertThrows(RuntimeException.class, () -> consumer.consume(record, ack));
        verify(ack, never()).acknowledge();
        verify(notificationRecords, never()).addIfAbsent(any(NotificationRecord.class));
    }

}
