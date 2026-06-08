package com.example.notify.domain.strategy;

import java.util.List;
import java.util.Optional;

public interface Strategies {

    List<Strategy> list();

    Optional<Strategy> find(StrategyId strategyId);

    Optional<Strategy> findByIdempotencyKey(IdempotencyKey idempotencyKey);

    Optional<String> fingerprint(IdempotencyKey idempotencyKey);

    Optional<IdempotencyEntry> findIdempotency(IdempotencyKey idempotencyKey);

    void save(Strategy strategy, IdempotencyKey idempotencyKey, String fingerprint);

    void delete(StrategyId strategyId);

    record IdempotencyEntry(StrategyId strategyId, String fingerprint) {}

}
