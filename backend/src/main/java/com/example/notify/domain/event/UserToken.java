package com.example.notify.domain.event;

public record UserToken(String value) {

    public UserToken {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("userToken must not be blank");
        }
        value = value.trim();
    }

    @Override
    public String toString() {
        return value;
    }

}
