package com.example.notify.domain.event;

public record CustomerId(String value) {

    public CustomerId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("customerId must not be blank");
        }
        value = value.trim();
    }

    @Override
    public String toString() {
        return value;
    }

}
