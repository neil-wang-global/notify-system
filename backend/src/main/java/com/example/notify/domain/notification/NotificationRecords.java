package com.example.notify.domain.notification;

public interface NotificationRecords {

    boolean contains(NotificationId notificationId);

    void add(NotificationRecord record);

}
