package com.example.notify.interfaces.rest;

import com.example.notify.domain.notification.NotificationRecord;
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
    public List<NotificationRecord> list() {
        return List.copyOf(records.get());
    }

}
