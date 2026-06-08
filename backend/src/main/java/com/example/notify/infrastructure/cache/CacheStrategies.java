package com.example.notify.infrastructure.cache;

import com.example.notify.application.port.CacheInvalidationPort;
import com.example.notify.domain.strategy.Strategy;
import com.example.notify.domain.strategy.StrategyExecutionPlan;
import com.example.notify.domain.strategy.StrategyId;
import com.example.notify.domain.strategy.StrategyVersion;
import com.example.notify.engine.matching.CandidateStrategyLookup;
import com.github.benmanes.caffeine.cache.Cache;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class CacheStrategies implements CandidateStrategyLookup, CacheInvalidationPort {

    private final CandidateStrategyLookup delegate;
    private final Cache<StrategyId, CacheStrategy> localCache;
    private final Cache<CandidateKey, Set<StrategyId>> candidateCache;

    public CacheStrategies(CandidateStrategyLookup delegate,
                           Cache<StrategyId, CacheStrategy> localCache,
                           Cache<CandidateKey, Set<StrategyId>> candidateCache) {
        if (delegate == null) { throw new IllegalArgumentException("delegate must not be null"); }
        if (localCache == null) { throw new IllegalArgumentException("localCache must not be null"); }
        if (candidateCache == null) { throw new IllegalArgumentException("candidateCache must not be null"); }
        this.delegate = delegate;
        this.localCache = localCache;
        this.candidateCache = candidateCache;
    }

    @Override
    public Set<StrategyId> candidates(String customerId, String userId, Set<String> userGroupIds, String eventType, Map<String, String> fields) {
        CandidateKey key = new CandidateKey(customerId, userId, userGroupIds, eventType, fields);
        Set<StrategyId> cached = candidateCache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        Set<StrategyId> result = delegate.candidates(customerId, userId, userGroupIds, eventType, fields);
        candidateCache.put(key, result);
        return result;
    }

    @Override
    public Optional<StrategyExecutionPlan> plan(StrategyId strategyId) {
        CacheStrategy cached = localCache.getIfPresent(strategyId);
        if (cached != null) {
            return Optional.of(cached.executionPlan());
        }
        Optional<StrategyExecutionPlan> plan = delegate.plan(strategyId);
        // T-23: The real version is only known after refresh(). Storing version=0 here is safe
        // because invalidate() always proceeds when cached version < incoming version, and 0
        // is less than any real version. The version guard becomes effective after the first
        // refresh or invalidation cycle.
        plan.ifPresent(p -> {
            localCache.put(strategyId, new CacheStrategy(strategyId, new StrategyVersion(0), p));
        });
        return plan;
    }

    @Override
    public boolean refresh(Strategy strategy) {
        boolean result = delegate.refresh(strategy);
        if (result) {
            localCache.put(
                strategy.id(),
                new CacheStrategy(strategy.id(), strategy.version(), strategy.executionPlan())
            );
            candidateCache.invalidateAll();
        }
        return result;
    }

    @Override
    public void invalidate(StrategyId strategyId, StrategyVersion version) {
        CacheStrategy existing = localCache.getIfPresent(strategyId);
        if (existing != null && version.value() < existing.version().value()) {
            return;
        }
        localCache.invalidate(strategyId);
        candidateCache.invalidateAll();
    }

    @Override
    public void evict(StrategyId strategyId) {
        localCache.invalidate(strategyId);
        candidateCache.invalidateAll();
        delegate.evict(strategyId);
    }

    public record CandidateKey(String customerId, String userId, Set<String> userGroupIds, String eventType, Map<String, String> fields) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CandidateKey that)) return false;
            return Objects.equals(customerId, that.customerId)
                && Objects.equals(userId, that.userId)
                && Objects.equals(userGroupIds, that.userGroupIds)
                && Objects.equals(eventType, that.eventType)
                && Objects.equals(fields, that.fields);
        }

        @Override
        public int hashCode() {
            return Objects.hash(customerId, userId, userGroupIds, eventType, fields);
        }
    }

}
