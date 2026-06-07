package com.example.notify.application.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.notify.domain.event.CustomerId;
import com.example.notify.domain.event.EventId;
import com.example.notify.domain.event.EventType;
import com.example.notify.domain.event.UserId;
import com.example.notify.domain.notification.NotificationEvent;
import com.example.notify.domain.notification.NotificationId;
import com.example.notify.domain.notification.NotificationRecord;
import com.example.notify.domain.notification.NotificationRecords;
import com.example.notify.domain.strategy.StrategyId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PersistNotificationTest {

    @Test
    void uniqueNotificationIdPreventsDuplicateInserts() {
        InMemoryNotificationRecords records = new InMemoryNotificationRecords();
        PersistNotification persistNotification = new PersistNotification(records);
        NotificationEvent event = event(new NotificationId("notification-1"));

        persistNotification.persist(event);
        persistNotification.persist(event);

        assertEquals(1, records.records.size());
        assertEquals(2, records.insertAttempts);
    }

    private static NotificationEvent event(NotificationId notificationId) {
        return new NotificationEvent(
            notificationId,
            new StrategyId("strategy-1"),
            new CustomerId("customer-1"),
            new UserId("user-1"),
            new EventId("event-1"),
            new EventType("PRODUCT_VIEW"),
            Instant.parse("2026-06-07T00:00:00Z"),
            "30s",
            3,
            3,
            "dedupe-1"
        );
    }

    private static final class InMemoryNotificationRecords implements NotificationRecords {
        private final List<NotificationRecord> records = new ArrayList<>();
        private int insertAttempts;

        @Override
        public boolean addIfAbsent(NotificationRecord record) {
            insertAttempts++;
            if (records.stream().anyMatch(existing -> existing.notificationId().equals(record.notificationId()))) {
                return false;
            }
            records.add(record);
            return true;
        }
    }

}
