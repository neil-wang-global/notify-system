package com.example.notify.infrastructure.redis;

import com.example.notify.domain.strategy.StrategyExecutionPlan;
import com.example.notify.domain.strategy.StrategyId;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Real Redis-backed strategy storage and candidate lookup.
 * <p>
 * Delegates to {@link RedisStrategyCache} for execution plan + metadata storage
 * with version guard, and {@link RedisCandidateIndex} for scope/event-type/field
 * index management.
 * <p>
 * On refresh, loads the previous metadata from Redis to clean up old index entries,
 * then writes new plan + metadata and adds new index entries.
 * <p>
 * This is the production implementation; {@link RedisStrategies} remains as the
 * in-memory fallback for environments where Redis is not available.
 */
public final class RealRedisStrategies {

    private final RedisStrategyCache cache;
    private final RedisCandidateIndex candidateIndex;

    public RealRedisStrategies(StringRedisTemplate redis) {
        if (redis == null) {
            throw new IllegalArgumentException("StringRedisTemplate must not be null");
        }
        this.cache = new RedisStrategyCache(redis);
        this.candidateIndex = new RedisCandidateIndex(redis);
    }

    /**
     * Refresh a strategy: save plan + metadata (with version guard), clean up old
     * indexes, and add new indexes.
     *
     * @param strategy the strategy to refresh
     * @return true if accepted, false if rejected by stale version
     */
    public boolean refresh(RedisStrategy strategy) {
        if (strategy == null) {
            throw new IllegalArgumentException("redis strategy must not be null");
        }

        // Load previous metadata for cleanup (before the version guard write)
        Optional<RedisStrategy> previous = cache.load(strategy.strategyId());

        // Save with version guard (Lua atomic check-and-set)
        boolean accepted = cache.save(strategy);
        if (!accepted) {
            return false;
        }

        // Remove old indexes using previous metadata
        previous.ifPresent(old -> candidateIndex.remove(
                old.strategyId(), old.scope(), old.eventType(), old.fieldIndexes()));

        // Add new indexes
        candidateIndex.index(strategy.strategyId(), strategy.scope(), strategy.eventType(), strategy.fieldIndexes());

        return true;
    }

    /**
     * Load an execution plan by strategy ID.
     */
    public Optional<StrategyExecutionPlan> plan(StrategyId strategyId) {
        return cache.loadPlan(strategyId);
    }

    /**
     * Compute candidate strategy IDs for the given event context.
     */
    public Set<StrategyId> candidates(String customerId, String userId, Set<String> userGroupIds,
                                       String eventType, Map<String, String> fields) {
        return candidateIndex.candidates(customerId, userId, userGroupIds, eventType, fields);
    }
}
