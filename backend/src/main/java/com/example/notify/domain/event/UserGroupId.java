package com.example.notify.domain.event;

public record UserGroupId(String value) {

    public UserGroupId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("userGroupId must not be blank");
        }
        value = value.trim();
    }

    @Override
    public String toString() {
        return value;
    }

}
