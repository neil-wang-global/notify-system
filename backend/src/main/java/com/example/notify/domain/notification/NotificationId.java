package com.example.notify.domain.notification;

public record NotificationId(String value) {

    public NotificationId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("notificationId must not be blank");
        }
        value = value.trim();
    }

    @Override
    public String toString() {
        return value;
    }

}
