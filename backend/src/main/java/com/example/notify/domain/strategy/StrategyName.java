package com.example.notify.domain.strategy;

public record StrategyName(String value) {

    public StrategyName {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("strategyName must not be blank");
        }
        value = value.trim();
    }

    @Override
    public String toString() {
        return value;
    }

}
