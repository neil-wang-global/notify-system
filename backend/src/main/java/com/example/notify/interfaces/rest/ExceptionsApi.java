package com.example.notify.interfaces.rest;

import com.example.notify.domain.exception.NotificationExceptionRecord;
import com.example.notify.domain.exception.UserOperationExceptionRecord;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/exceptions")
public final class ExceptionsApi {

    private final Supplier<List<UserOperationExceptionRecord>> userOperationExceptions;
    private final Supplier<List<NotificationExceptionRecord>> notificationExceptions;

    public ExceptionsApi(Supplier<List<UserOperationExceptionRecord>> userOperationExceptions, Supplier<List<NotificationExceptionRecord>> notificationExceptions) {
        if (userOperationExceptions == null || notificationExceptions == null) {
            throw new IllegalArgumentException("exception suppliers must not be null");
        }
        this.userOperationExceptions = userOperationExceptions;
        this.notificationExceptions = notificationExceptions;
    }

    @GetMapping("/user-operations")
    public List<UserOperationExceptionResponse> userOperationExceptions() {
        return userOperationExceptions.get().stream()
            .map(UserOperationExceptionResponse::from)
            .toList();
    }

    @GetMapping("/notifications")
    public List<NotificationExceptionResponse> notificationExceptions() {
        return notificationExceptions.get().stream()
            .map(NotificationExceptionResponse::from)
            .toList();
    }

    public record UserOperationExceptionResponse(
        String id, String eventId, String customerId, String eventType,
        String payload, String failureReason, int retryCount, String status, Instant createdAt
    ) {
        static UserOperationExceptionResponse from(UserOperationExceptionRecord record) {
            return new UserOperationExceptionResponse(
                record.id(),
                record.eventId().value(),
                record.customerId().value(),
                record.eventType().value(),
                record.payload(),
                record.failureReason(),
                record.retryCount(),
                record.status(),
                record.createdAt()
            );
        }
    }

    public record NotificationExceptionResponse(
        String id, String notificationId, String strategyId, String customerId, String eventId,
        String payload, String failureReason, int retryCount, String status, Instant createdAt
    ) {
        static NotificationExceptionResponse from(NotificationExceptionRecord record) {
            return new NotificationExceptionResponse(
                record.id(),
                record.notificationId().value(),
                record.strategyId().value(),
                record.customerId().value(),
                record.eventId().value(),
                record.payload(),
                record.failureReason(),
                record.retryCount(),
                record.status(),
                record.createdAt()
            );
        }
    }

}
