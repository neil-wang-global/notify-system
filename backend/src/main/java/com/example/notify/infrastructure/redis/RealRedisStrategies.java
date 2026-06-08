package com.example.notify.infrastructure.redis;

import com.example.notify.domain.strategy.Strategy;
import com.example.notify.domain.strategy.StrategyExecutionPlan;
import com.example.notify.domain.strategy.StrategyId;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;

public final class RealRedisStrategies implements CandidateStrategyLookup {
    private final RedisStrategyCache cache;
    private final RedisCandidateIndex candidateIndex;

    public RealRedisStrategies(StringRedisTemplate redis) {
        if (redis == null) { throw new IllegalArgumentException("StringRedisTemplate must not be null"); }
        this.cache = new RedisStrategyCache(redis);
        this.candidateIndex = new RedisCandidateIndex(redis);
    }

    @Override
    public boolean refresh(Strategy strategy) {
        if (strategy == null) { throw new IllegalArgumentException("strategy must not be null"); }
        RedisStrategy rs = RedisStrategy.from(strategy);
        Optional<RedisStrategy> previous = cache.load(strategy.id());
        if (!cache.save(rs)) { return false; }
        previous.ifPresent(old -> candidateIndex.remove(old.strategyId(), old.scope(), old.eventType(), old.fieldIndexes()));
        candidateIndex.index(rs.strategyId(), rs.scope(), rs.eventType(), rs.fieldIndexes());
        return true;
    }

    @Override
    public Optional<StrategyExecutionPlan> plan(StrategyId strategyId) { return cache.loadPlan(strategyId); }

    @Override
    public Set<StrategyId> candidates(String customerId, String userId, Set<String> userGroupIds, String eventType, Map<String, String> fields) {
        return candidateIndex.candidates(customerId, userId, userGroupIds, eventType, fields);
    }
}
