package com.example.notify.domain.notification;

import java.time.Instant;

public record NotificationRecord(NotificationId notificationId, NotificationEvent event, Instant persistedAt) {

    public NotificationRecord {
        if (notificationId == null || event == null || persistedAt == null) {
            throw new IllegalArgumentException("notification record is incomplete");
        }
    }

    public static NotificationRecord from(NotificationEvent event) {
        return new NotificationRecord(event.notificationId(), event, event.triggeredAt());
    }

}
