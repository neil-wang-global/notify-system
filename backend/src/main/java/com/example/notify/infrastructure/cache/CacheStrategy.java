package com.example.notify.infrastructure.cache;

import com.example.notify.domain.strategy.StrategyExecutionPlan;
import com.example.notify.domain.strategy.StrategyId;
import com.example.notify.domain.strategy.StrategyVersion;

public record CacheStrategy(StrategyId strategyId, StrategyVersion version, StrategyExecutionPlan executionPlan) {

    public CacheStrategy {
        if (strategyId == null || version == null || executionPlan == null) {
            throw new IllegalArgumentException("cache strategy is incomplete");
        }
    }

}
