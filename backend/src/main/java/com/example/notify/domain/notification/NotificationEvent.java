package com.example.notify.domain.notification;

import com.example.notify.domain.event.CustomerId;
import com.example.notify.domain.event.EventId;
import com.example.notify.domain.event.EventType;
import com.example.notify.domain.event.UserId;
import com.example.notify.domain.strategy.StrategyId;
import java.time.Instant;

public record NotificationEvent(
    NotificationId notificationId,
    StrategyId strategyId,
    CustomerId customerId,
    UserId userId,
    EventId eventId,
    EventType eventType,
    Instant triggeredAt,
    String window,
    int threshold,
    int currentCount,
    String dedupeKey
) {

    public NotificationEvent {
        if (notificationId == null || strategyId == null || customerId == null || userId == null || eventId == null || eventType == null || triggeredAt == null) {
            throw new IllegalArgumentException("notification event identity is incomplete");
        }
        if (window == null || window.isBlank() || threshold < 1 || currentCount < 1 || dedupeKey == null || dedupeKey.isBlank()) {
            throw new IllegalArgumentException("notification event payload is incomplete");
        }
        window = window.trim();
        dedupeKey = dedupeKey.trim();
    }

}
