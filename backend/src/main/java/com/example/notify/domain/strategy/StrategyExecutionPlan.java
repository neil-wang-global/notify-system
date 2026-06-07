package com.example.notify.domain.strategy;

public record StrategyExecutionPlan(String value) {

    public StrategyExecutionPlan {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("strategyExecutionPlan must not be blank");
        }
        value = value.trim();
    }

    @Override
    public String toString() {
        return value;
    }

}
