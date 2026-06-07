package com.example.notify.application.notification;

import com.example.notify.domain.notification.NotificationEvent;
import com.example.notify.domain.notification.NotificationRecord;
import com.example.notify.domain.notification.NotificationRecords;

public final class PersistNotification {

    private final NotificationRecords records;

    public PersistNotification(NotificationRecords records) {
        if (records == null) {
            throw new IllegalArgumentException("notificationRecords must not be null");
        }
        this.records = records;
    }

    public void persist(NotificationEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("notification event must not be null");
        }
        records.addIfAbsent(NotificationRecord.from(event));
    }

}
