package com.example.notify.domain.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.notify.domain.event.CustomerId;
import com.example.notify.domain.event.EventId;
import com.example.notify.domain.event.EventType;
import com.example.notify.domain.notification.NotificationId;
import com.example.notify.domain.strategy.StrategyId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ExceptionRecordsTest {

    @Test
    void userOperationExceptionRecordCapturesFailure() {
        UserOperationExceptionRecord record = new UserOperationExceptionRecord(
            "exception-1",
            new EventId("event-1"),
            new CustomerId("customer-1"),
            new EventType("PRODUCT_VIEW"),
            "{}",
            "redis unavailable",
            3,
            "FAILED",
            Instant.parse("2026-06-07T00:00:00Z"),
            Instant.parse("2026-06-07T00:01:00Z")
        );

        assertEquals("redis unavailable", record.failureReason());
        assertEquals(Instant.parse("2026-06-07T00:01:00Z"), record.updatedAt());
    }

    @Test
    void notificationExceptionRecordCapturesFailure() {
        NotificationExceptionRecord record = new NotificationExceptionRecord(
            "exception-1",
            new NotificationId("notification-1"),
            new StrategyId("strategy-1"),
            new CustomerId("customer-1"),
            new EventId("event-1"),
            "{}",
            "publish failed",
            3,
            "FAILED",
            Instant.parse("2026-06-07T00:00:00Z"),
            Instant.parse("2026-06-07T00:01:00Z")
        );

        assertEquals("publish failed", record.failureReason());
        assertEquals(Instant.parse("2026-06-07T00:01:00Z"), record.updatedAt());
    }

    @Test
    void rejectsMissingFailureReason() {
        assertThrows(IllegalArgumentException.class, () -> new UserOperationExceptionRecord(
            "exception-1",
            new EventId("event-1"),
            new CustomerId("customer-1"),
            new EventType("PRODUCT_VIEW"),
            "{}",
            " ",
            3,
            "FAILED",
            Instant.parse("2026-06-07T00:00:00Z"),
            Instant.parse("2026-06-07T00:01:00Z")
        ));
    }

    @Test
    void userOperationExceptionsAreQueryableById() {
        InMemoryUserOperationExceptions exceptions = new InMemoryUserOperationExceptions();
        UserOperationExceptionRecord record = new UserOperationExceptionRecord(
            "exception-1",
            new EventId("event-1"),
            new CustomerId("customer-1"),
            new EventType("PRODUCT_VIEW"),
            "{}",
            "redis unavailable",
            3,
            "FAILED",
            Instant.parse("2026-06-07T00:00:00Z"),
            Instant.parse("2026-06-07T00:01:00Z")
        );

        exceptions.add(record);

        assertTrue(exceptions.find("exception-1").isPresent());
    }

    @Test
    void notificationExceptionsAreQueryableById() {
        InMemoryNotificationExceptions exceptions = new InMemoryNotificationExceptions();
        NotificationExceptionRecord record = new NotificationExceptionRecord(
            "exception-1",
            new NotificationId("notification-1"),
            new StrategyId("strategy-1"),
            new CustomerId("customer-1"),
            new EventId("event-1"),
            "{}",
            "publish failed",
            3,
            "FAILED",
            Instant.parse("2026-06-07T00:00:00Z"),
            Instant.parse("2026-06-07T00:01:00Z")
        );

        exceptions.add(record);

        assertTrue(exceptions.find("exception-1").isPresent());
    }

    private static final class InMemoryUserOperationExceptions implements UserOperationExceptions {
        private final List<UserOperationExceptionRecord> records = new ArrayList<>();

        @Override
        public void add(UserOperationExceptionRecord record) {
            records.add(record);
        }

        @Override
        public Optional<UserOperationExceptionRecord> find(String id) {
            return records.stream().filter(record -> record.id().equals(id)).findFirst();
        }
    }

    private static final class InMemoryNotificationExceptions implements NotificationExceptions {
        private final List<NotificationExceptionRecord> records = new ArrayList<>();

        @Override
        public void add(NotificationExceptionRecord record) {
            records.add(record);
        }

        @Override
        public Optional<NotificationExceptionRecord> find(String id) {
            return records.stream().filter(record -> record.id().equals(id)).findFirst();
        }
    }

}
