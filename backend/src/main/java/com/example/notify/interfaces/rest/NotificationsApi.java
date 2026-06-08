package com.example.notify.interfaces.rest;

import com.example.notify.domain.notification.NotificationEvent;
import com.example.notify.domain.notification.NotificationRecord;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public final class NotificationsApi {

    private final Supplier<List<NotificationRecord>> records;

    public NotificationsApi(Supplier<List<NotificationRecord>> records) {
        if (records == null) {
            throw new IllegalArgumentException("notification records supplier must not be null");
        }
        this.records = records;
    }

    @GetMapping
    public List<NotificationResponse> list() {
        return records.get().stream()
            .map(NotificationResponse::from)
            .toList();
    }

    public record NotificationResponse(
        String notificationId, String strategyId, String customerId, String userId,
        String eventId, String eventType, Instant triggeredAt, int currentCount, int threshold
    ) {
        static NotificationResponse from(NotificationRecord record) {
            NotificationEvent event = record.event();
            return new NotificationResponse(
                event.notificationId().value(),
                event.strategyId().value(),
                event.customerId().value(),
                event.userId().value(),
                event.eventId().value(),
                event.eventType().value(),
                event.triggeredAt(),
                event.currentCount(),
                event.threshold()
            );
        }
    }

}
