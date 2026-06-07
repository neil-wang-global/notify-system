package com.example.notify.domain.strategy;

public record StrategyVersion(int value) {

    public StrategyVersion {
        if (value < 1) {
            throw new IllegalArgumentException("strategyVersion must be positive");
        }
    }

    @Override
    public String toString() {
        return Integer.toString(value);
    }

}
