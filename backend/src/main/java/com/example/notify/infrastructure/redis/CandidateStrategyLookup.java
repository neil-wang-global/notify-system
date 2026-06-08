package com.example.notify.infrastructure.redis;

import com.example.notify.domain.strategy.Strategy;
import com.example.notify.domain.strategy.StrategyExecutionPlan;
import com.example.notify.domain.strategy.StrategyId;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface CandidateStrategyLookup {
    Set<StrategyId> candidates(String customerId, String userId, Set<String> userGroupIds, String eventType, Map<String, String> fields);
    Optional<StrategyExecutionPlan> plan(StrategyId strategyId);
    boolean refresh(Strategy strategy);
}
