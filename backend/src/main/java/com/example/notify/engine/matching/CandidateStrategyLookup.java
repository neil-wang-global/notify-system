package com.example.notify.engine.matching;

import com.example.notify.domain.strategy.Strategy;
import com.example.notify.domain.strategy.StrategyExecutionPlan;
import com.example.notify.domain.strategy.StrategyId;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Port for looking up candidate strategies and their execution plans.
 * Implemented by Redis/infrastructure adapters; consumed by REST controllers,
 * Kafka consumers, and the caching layer.
 */
public interface CandidateStrategyLookup {
    Set<StrategyId> candidates(String customerId, String userId, Set<String> userGroupIds, String eventType, Map<String, String> fields);
    Optional<StrategyExecutionPlan> plan(StrategyId strategyId);
    boolean refresh(Strategy strategy);
    void evict(StrategyId strategyId);
}
