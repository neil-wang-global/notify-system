package com.example.notify.infrastructure.cache;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.notify.domain.strategy.StrategyExecutionPlan;
import com.example.notify.domain.strategy.StrategyId;
import com.example.notify.domain.strategy.StrategyVersion;
import org.junit.jupiter.api.Test;

class CacheStrategiesVersionGuardTest {

    @Test
    void acceptsFreshAndEqualVersionWrites() {
        CacheStrategies cacheStrategies = new CacheStrategies();
        StrategyId strategyId = new StrategyId("strategy-1");

        assertTrue(cacheStrategies.refresh(strategyId, new StrategyVersion(1), new StrategyExecutionPlan("plan-v1")));
        assertTrue(cacheStrategies.refresh(strategyId, new StrategyVersion(1), new StrategyExecutionPlan("plan-v1-repeat")));
        assertTrue(cacheStrategies.refresh(strategyId, new StrategyVersion(2), new StrategyExecutionPlan("plan-v2")));
    }

    @Test
    void rejectsStaleWrites() {
        CacheStrategies cacheStrategies = new CacheStrategies();
        StrategyId strategyId = new StrategyId("strategy-1");

        assertTrue(cacheStrategies.refresh(strategyId, new StrategyVersion(2), new StrategyExecutionPlan("plan-v2")));
        assertFalse(cacheStrategies.refresh(strategyId, new StrategyVersion(1), new StrategyExecutionPlan("plan-v1")));
    }

}
