package com.example.notify.domain.exception;

import com.example.notify.domain.event.CustomerId;
import com.example.notify.domain.event.EventId;
import com.example.notify.domain.notification.NotificationId;
import com.example.notify.domain.strategy.StrategyId;
import java.time.Instant;

public record NotificationExceptionRecord(
    String id,
    NotificationId notificationId,
    StrategyId strategyId,
    CustomerId customerId,
    EventId eventId,
    String payload,
    String failureReason,
    int retryCount,
    String status,
    Instant createdAt
) {

    public NotificationExceptionRecord {
        requireText(id, "exception id");
        requireText(payload, "exception payload");
        requireText(failureReason, "failure reason");
        requireText(status, "exception status");
        if (notificationId == null || strategyId == null || customerId == null || eventId == null || createdAt == null || retryCount < 0) {
            throw new IllegalArgumentException("notification exception record is incomplete");
        }
        id = id.trim();
        payload = payload.trim();
        failureReason = failureReason.trim();
        status = status.trim();
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

}
