package com.example.notify.infrastructure.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.notify.domain.strategy.RuleAst;
import com.example.notify.domain.strategy.Strategy;
import com.example.notify.domain.strategy.StrategyExecutionPlan;
import com.example.notify.domain.strategy.StrategyId;
import com.example.notify.domain.strategy.StrategyName;
import com.example.notify.domain.strategy.StrategyScope;
import com.example.notify.domain.strategy.StrategyVersion;
import com.example.notify.engine.matching.CandidateStrategyLookup;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CacheStrategiesVersionGuardTest {

    private final CandidateStrategyLookup delegate = mock(CandidateStrategyLookup.class);
    private final Cache<StrategyId, CacheStrategy> localCache = Caffeine.newBuilder().build();
    private final Cache<CacheStrategies.CandidateKey, Set<StrategyId>> candidateCache = Caffeine.newBuilder().build();
    private final CacheStrategies cacheStrategies = new CacheStrategies(delegate, localCache, candidateCache);

    private static final StrategyExecutionPlan PLAN_V1 = new StrategyExecutionPlan(Duration.ofSeconds(30), Duration.ofSeconds(10), Duration.ZERO, List.of("customerId"));
    private static final StrategyExecutionPlan PLAN_V2 = new StrategyExecutionPlan(Duration.ofSeconds(60), Duration.ofSeconds(10), Duration.ZERO, List.of("customerId"));

    @Test
    void refreshPopulatesCacheWhenDelegateSucceeds() {
        Strategy strategy = strategy("strategy-1", 1, PLAN_V1);
        when(delegate.refresh(any(Strategy.class))).thenReturn(true);

        assertTrue(cacheStrategies.refresh(strategy));

        CacheStrategy cached = localCache.getIfPresent(strategy.id());
        assertEquals(strategy.id(), cached.strategyId());
        assertEquals(strategy.version(), cached.version());
    }

    @Test
    void refreshDoesNotPopulateCacheWhenDelegateFails() {
        Strategy strategy = strategy("strategy-1", 1, PLAN_V1);
        when(delegate.refresh(any(Strategy.class))).thenReturn(false);

        assertFalse(cacheStrategies.refresh(strategy));
        assertFalse(localCache.asMap().containsKey(strategy.id()));
    }

    @Test
    void invalidateRemovesCachedEntry() {
        Strategy strategy = strategy("strategy-1", 2, PLAN_V2);
        when(delegate.refresh(any(Strategy.class))).thenReturn(true);
        cacheStrategies.refresh(strategy);

        cacheStrategies.invalidate(strategy.id(), strategy.version());

        assertFalse(localCache.asMap().containsKey(strategy.id()));
    }

    @Test
    void planReturnsCachedValue() {
        Strategy strategy = strategy("strategy-1", 1, PLAN_V1);
        when(delegate.refresh(any(Strategy.class))).thenReturn(true);
        cacheStrategies.refresh(strategy);

        Optional<StrategyExecutionPlan> result = cacheStrategies.plan(strategy.id());
        assertTrue(result.isPresent());
        assertEquals(PLAN_V1, result.get());
    }

    @Test
    void candidatesCachesResult() {
        when(delegate.candidates("c1", "u1", Set.of(), "VIEW", Map.of())).thenReturn(Set.of(new StrategyId("s1")));

        Set<StrategyId> first = cacheStrategies.candidates("c1", "u1", Set.of(), "VIEW", Map.of());
        Set<StrategyId> second = cacheStrategies.candidates("c1", "u1", Set.of(), "VIEW", Map.of());

        assertEquals(1, first.size());
        assertEquals(first, second);
    }

    @Test
    void refreshInvalidatesCandidateCache() {
        when(delegate.candidates("c1", "u1", Set.of(), "VIEW", Map.of())).thenReturn(Set.of(new StrategyId("s1")));
        cacheStrategies.candidates("c1", "u1", Set.of(), "VIEW", Map.of());
        assertFalse(candidateCache.asMap().isEmpty());

        Strategy strategy = strategy("strategy-1", 1, PLAN_V1);
        when(delegate.refresh(any(Strategy.class))).thenReturn(true);
        cacheStrategies.refresh(strategy);

        assertTrue(candidateCache.asMap().isEmpty());
    }

    private static Strategy strategy(String id, int version, StrategyExecutionPlan plan) {
        return new Strategy(
            new StrategyId(id),
            new StrategyName("test"),
            StrategyScope.global(),
            new RuleAst.Comparison("eventType", com.example.notify.domain.strategy.RuleOperator.EQ, "VIEW"),
            plan,
            1,
            new StrategyVersion(version)
        );
    }

}
