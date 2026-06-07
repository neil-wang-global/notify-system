package com.example.notify.interfaces.rest;

import com.example.notify.domain.notification.NotificationRecord;
import java.util.List;
import java.util.function.Supplier;

public final class NotificationsApi {

    private final Supplier<List<NotificationRecord>> records;

    public NotificationsApi(Supplier<List<NotificationRecord>> records) {
        if (records == null) {
            throw new IllegalArgumentException("notification records supplier must not be null");
        }
        this.records = records;
    }

    public List<NotificationRecord> list() {
        return List.copyOf(records.get());
    }

}
