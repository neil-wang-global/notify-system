package com.example.notify.domain.event;

public record EventId(String value) {

    public EventId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
        value = value.trim();
    }

    @Override
    public String toString() {
        return value;
    }

}
