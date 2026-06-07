package com.example.notify.engine.matching;

import java.util.Map;
import java.util.Set;

public record EventSnapshot(String customerId, String userId, Set<String> userGroupIds, String eventType, Map<String, String> fields) {

    public EventSnapshot {
        if (customerId == null || customerId.isBlank() || userId == null || userId.isBlank() || eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("event snapshot identity is incomplete");
        }
        if (userGroupIds == null || fields == null) {
            throw new IllegalArgumentException("event snapshot collections must not be null");
        }
        customerId = customerId.trim();
        userId = userId.trim();
        eventType = eventType.trim();
        userGroupIds = Set.copyOf(userGroupIds);
        fields = Map.copyOf(fields);
    }

    public String value(String field) {
        if ("customerId".equals(field)) {
            return customerId;
        }
        if ("userId".equals(field)) {
            return userId;
        }
        if ("eventType".equals(field)) {
            return eventType;
        }
        return fields.get(field);
    }

}
