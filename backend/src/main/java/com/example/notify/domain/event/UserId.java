package com.example.notify.domain.event;

public record UserId(String value) {

    public UserId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        value = value.trim();
    }

    @Override
    public String toString() {
        return value;
    }

}
