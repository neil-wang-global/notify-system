package com.example.notify.domain.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.notify.domain.event.CustomerId;
import com.example.notify.domain.event.EventId;
import com.example.notify.domain.event.EventType;
import com.example.notify.domain.notification.NotificationId;
import com.example.notify.domain.strategy.StrategyId;
import java.time.Instant;
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
            Instant.parse("2026-06-07T00:00:00Z")
        );

        assertEquals("redis unavailable", record.failureReason());
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
            Instant.parse("2026-06-07T00:00:00Z")
        );

        assertEquals("publish failed", record.failureReason());
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
            Instant.parse("2026-06-07T00:00:00Z")
        ));
    }

}
