package com.example.notify.domain.event;

public record EventType(String value) {

    public EventType {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        value = value.trim();
    }

    @Override
    public String toString() {
        return value;
    }

}
