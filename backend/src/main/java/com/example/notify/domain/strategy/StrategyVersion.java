package com.example.notify.domain.strategy;

public record StrategyVersion(int value) {

    public StrategyVersion {
        if (value < 1) {
            throw new IllegalArgumentException("strategyVersion must be positive");
        }
    }

    public StrategyVersion next() {
        return new StrategyVersion(value + 1);
    }

    @Override
    public String toString() {
        return Integer.toString(value);
    }

}
