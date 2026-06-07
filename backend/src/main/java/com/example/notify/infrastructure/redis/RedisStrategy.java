package com.example.notify.infrastructure.redis;

import com.example.notify.domain.event.EventType;
import com.example.notify.domain.strategy.StrategyExecutionPlan;
import com.example.notify.domain.strategy.StrategyId;
import com.example.notify.domain.strategy.StrategyScope;
import com.example.notify.domain.strategy.StrategyVersion;
import java.util.List;

public record RedisStrategy(
    StrategyId strategyId,
    StrategyVersion version,
    StrategyExecutionPlan executionPlan,
    StrategyScope scope,
    EventType eventType,
    List<RedisFieldIndex> fieldIndexes
) {

    public RedisStrategy {
        if (strategyId == null || version == null || executionPlan == null || scope == null || eventType == null || fieldIndexes == null) {
            throw new IllegalArgumentException("redis strategy is incomplete");
        }
        fieldIndexes = List.copyOf(fieldIndexes);
    }

}
