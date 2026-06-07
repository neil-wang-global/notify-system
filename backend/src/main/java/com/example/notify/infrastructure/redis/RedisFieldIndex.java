package com.example.notify.infrastructure.redis;

public record RedisFieldIndex(String field, String value) {

    public RedisFieldIndex {
        if (field == null || field.isBlank() || value == null || value.isBlank()) {
            throw new IllegalArgumentException("redis field index is incomplete");
        }
        field = field.trim();
        value = value.trim();
    }

}
