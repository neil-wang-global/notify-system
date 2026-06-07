package com.example.notify.domain.strategy;

public record RuleGroup(String value) {

    public RuleGroup {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ruleGroup must not be blank");
        }
        value = value.trim();
    }

    @Override
    public String toString() {
        return value;
    }

}
