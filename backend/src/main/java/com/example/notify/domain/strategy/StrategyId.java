package com.example.notify.domain.strategy;

public record StrategyId(String value) {

    public StrategyId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("strategyId must not be blank");
        }
        value = value.trim();
    }

    @Override
    public String toString() {
        return value;
    }

}
