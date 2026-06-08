package com.example.notify.infrastructure.persistence;

import com.example.notify.domain.strategy.IdempotencyKey;
import com.example.notify.domain.strategy.Strategies;
import com.example.notify.domain.strategy.Strategy;
import com.example.notify.domain.strategy.StrategyId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class DbStrategies implements Strategies {

    private final Map<StrategyId, Strategy> strategies = new ConcurrentHashMap<>();
    private final Map<IdempotencyKey, StrategyId> idempotencyKeys = new ConcurrentHashMap<>();
    private final Map<IdempotencyKey, String> fingerprints = new ConcurrentHashMap<>();

    @Override
    public List<Strategy> list() { return List.copyOf(strategies.values()); }

    @Override
    public Optional<Strategy> find(StrategyId strategyId) {
        return Optional.ofNullable(strategies.get(strategyId));
    }

    @Override
    public Optional<Strategy> findByIdempotencyKey(IdempotencyKey idempotencyKey) {
        return Optional.ofNullable(idempotencyKeys.get(idempotencyKey)).flatMap(this::find);
    }

    @Override
    public Optional<String> fingerprint(IdempotencyKey idempotencyKey) {
        return Optional.ofNullable(fingerprints.get(idempotencyKey));
    }

    @Override
    public void save(Strategy strategy, IdempotencyKey idempotencyKey, String fingerprint) {
        if (strategy == null || idempotencyKey == null || fingerprint == null || fingerprint.isBlank()) {
            throw new IllegalArgumentException("strategy save state is incomplete");
        }
        strategies.put(strategy.id(), strategy);
        idempotencyKeys.put(idempotencyKey, strategy.id());
        fingerprints.put(idempotencyKey, fingerprint);
    }

}
