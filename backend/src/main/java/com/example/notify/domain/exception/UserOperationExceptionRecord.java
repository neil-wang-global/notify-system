package com.example.notify.domain.exception;

import com.example.notify.domain.event.CustomerId;
import com.example.notify.domain.event.EventId;
import com.example.notify.domain.event.EventType;
import java.time.Instant;

public record UserOperationExceptionRecord(
    String id,
    EventId eventId,
    CustomerId customerId,
    EventType eventType,
    String payload,
    String failureReason,
    int retryCount,
    String status,
    Instant createdAt,
    Instant updatedAt
) {

    public UserOperationExceptionRecord {
        requireText(id, "exception id");
        requireText(payload, "exception payload");
        requireText(failureReason, "failure reason");
        requireText(status, "exception status");
        if (eventId == null || customerId == null || eventType == null || createdAt == null || updatedAt == null || retryCount < 0) {
            throw new IllegalArgumentException("user operation exception record is incomplete");
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
