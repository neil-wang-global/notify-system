package com.example.notify.domain.strategy;

import java.time.Instant;

public record StrategySaved(StrategyId strategyId, StrategyVersion version, Instant occurredAt) {

    public StrategySaved {
        if (strategyId == null) {
            throw new IllegalArgumentException("strategyId must not be null");
        }
        if (version == null) {
            throw new IllegalArgumentException("version must not be null");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt must not be null");
        }
    }

}
