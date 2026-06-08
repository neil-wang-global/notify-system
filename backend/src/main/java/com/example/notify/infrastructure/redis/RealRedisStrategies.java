package com.example.notify.infrastructure.redis;

import com.example.notify.config.DegradationState;
import com.example.notify.domain.strategy.Strategy;
import com.example.notify.domain.strategy.StrategyExecutionPlan;
import com.example.notify.domain.strategy.StrategyId;
import com.example.notify.engine.matching.CandidateStrategyLookup;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;

public final class RealRedisStrategies implements CandidateStrategyLookup {
    private static final Logger log = LoggerFactory.getLogger(RealRedisStrategies.class);

    private final RedisStrategyCache cache;
    private final RedisCandidateIndex candidateIndex;
    private final DegradationState degradationState;

    public RealRedisStrategies(StringRedisTemplate redis, DegradationState degradationState) {
        if (redis == null) { throw new IllegalArgumentException("StringRedisTemplate must not be null"); }
        if (degradationState == null) { throw new IllegalArgumentException("DegradationState must not be null"); }
        this.cache = new RedisStrategyCache(redis);
        this.candidateIndex = new RedisCandidateIndex(redis);
        this.degradationState = degradationState;
    }

    @Override
    public boolean refresh(Strategy strategy) {
        if (strategy == null) { throw new IllegalArgumentException("strategy must not be null"); }
        try {
            RedisStrategy rs = RedisStrategy.from(strategy);
            Optional<RedisStrategy> previous = cache.load(strategy.id());
            if (!cache.save(rs)) { return false; }
            // T-15: use pipeline to batch index removal and addition into a single round-trip
            RedisStrategy old = previous.orElse(null);
            candidateIndex.reindex(
                rs.strategyId(),
                old != null ? old.scope() : null, old != null ? old.eventType() : null, old != null ? old.fieldIndexes() : null,
                rs.scope(), rs.eventType(), rs.fieldIndexes()
            );
            degradationState.recover();
            return true;
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            log.warn("Redis failure during refresh, degrading: {}", e.getMessage());
            degradationState.degrade("Redis unavailable: " + e.getMessage());
            throw e;
        }
    }

    @Override
    public void evict(StrategyId strategyId) {
        if (strategyId == null) { throw new IllegalArgumentException("strategyId must not be null"); }
        try {
            Optional<RedisStrategy> cached = cache.load(strategyId);
            cached.ifPresent(rs -> {
                candidateIndex.remove(rs.strategyId(), rs.scope(), rs.eventType(), rs.fieldIndexes());
                cache.redis().delete("strategy:plan:" + strategyId.value());
            });
            degradationState.recover();
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            log.warn("Redis failure during evict, degrading: {}", e.getMessage());
            degradationState.degrade("Redis unavailable: " + e.getMessage());
            throw e;
        }
    }

    @Override
    public Optional<StrategyExecutionPlan> plan(StrategyId strategyId) {
        try {
            Optional<StrategyExecutionPlan> result = cache.loadPlan(strategyId);
            degradationState.recover();
            return result;
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            log.warn("Redis failure during plan lookup, degrading: {}", e.getMessage());
            degradationState.degrade("Redis unavailable: " + e.getMessage());
            throw e;
        }
    }

    @Override
    public Set<StrategyId> candidates(String customerId, String userId, Set<String> userGroupIds, String eventType, Map<String, String> fields) {
        try {
            Set<StrategyId> result = candidateIndex.candidates(customerId, userId, userGroupIds, eventType, fields);
            degradationState.recover();
            return result;
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            log.warn("Redis failure during candidate lookup, degrading: {}", e.getMessage());
            degradationState.degrade("Redis unavailable: " + e.getMessage());
            throw e;
        }
    }
}
