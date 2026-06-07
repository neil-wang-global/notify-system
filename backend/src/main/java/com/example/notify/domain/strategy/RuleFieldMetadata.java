package com.example.notify.domain.strategy;

import java.util.Map;

public record RuleFieldMetadata(String field, RuleValueType valueType) {

    private static final Map<String, RuleValueType> FIXED_FIELDS = Map.of(
        "customerId", RuleValueType.STRING,
        "userId", RuleValueType.STRING,
        "eventType", RuleValueType.STRING,
        "actionCode", RuleValueType.STRING,
        "productId", RuleValueType.STRING,
        "channel", RuleValueType.STRING,
        "pageCode", RuleValueType.STRING,
        "source", RuleValueType.STRING,
        "occurredAt", RuleValueType.DATETIME
    );

    public RuleFieldMetadata {
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("rule field must not be blank");
        }
        if (valueType == null) {
            throw new IllegalArgumentException("rule value type must not be null");
        }
        field = field.trim();
    }

    public static boolean supports(String field) {
        return field != null && FIXED_FIELDS.containsKey(field.trim());
    }

    public static RuleFieldMetadata required(String field) {
        if (!supports(field)) {
            throw new IllegalArgumentException("unsupported rule field: " + field);
        }
        String normalizedField = field.trim();
        return new RuleFieldMetadata(normalizedField, FIXED_FIELDS.get(normalizedField));
    }

}
