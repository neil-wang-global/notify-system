package com.example.notify.domain.strategy;

import java.util.ArrayList;
import java.util.List;

public record RuleValue(RuleValueType type, Object raw) {

    public RuleValue {
        if (type == null) {
            throw new IllegalArgumentException("rule value type must not be null");
        }
        if (raw == null) {
            throw new IllegalArgumentException("rule value must not be null");
        }
    }

    public static RuleValue ofString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("string rule value must not be blank");
        }
        return new RuleValue(RuleValueType.STRING, value.trim());
    }

    public static RuleValue ofStrings(String firstValue, String secondValue, String... otherValues) {
        List<String> values = new ArrayList<>();
        values.add(normalizeString(firstValue));
        values.add(normalizeString(secondValue));
        for (String otherValue : otherValues) {
            values.add(normalizeString(otherValue));
        }
        return new RuleValue(RuleValueType.STRING_LIST, List.copyOf(values));
    }

    public static RuleValue ofNumber(Number value) {
        if (value == null) {
            throw new IllegalArgumentException("number rule value must not be null");
        }
        return new RuleValue(RuleValueType.NUMBER, value);
    }

    public static RuleValue ofNumbers(Number firstValue, Number secondValue, Number... otherValues) {
        List<Number> values = new ArrayList<>();
        values.add(requiredNumber(firstValue));
        values.add(requiredNumber(secondValue));
        for (Number otherValue : otherValues) {
            values.add(requiredNumber(otherValue));
        }
        return new RuleValue(RuleValueType.NUMBER_LIST, List.copyOf(values));
    }

    public static RuleValue ofBoolean(boolean value) {
        return new RuleValue(RuleValueType.BOOLEAN, value);
    }

    public static RuleValue ofDatetime(String value) {
        return new RuleValue(RuleValueType.DATETIME, normalizeString(value));
    }

    public static RuleValue ofDatetimes(String firstValue, String secondValue) {
        return new RuleValue(RuleValueType.DATETIME, List.of(normalizeString(firstValue), normalizeString(secondValue)));
    }

    private static String normalizeString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("rule value must not be blank");
        }
        return value.trim();
    }

    private static Number requiredNumber(Number value) {
        if (value == null) {
            throw new IllegalArgumentException("number rule value must not be null");
        }
        return value;
    }

}
