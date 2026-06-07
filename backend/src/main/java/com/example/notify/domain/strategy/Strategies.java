package com.example.notify.domain.strategy;

import java.util.Optional;

public interface Strategies {

    Optional<Strategy> find(StrategyId strategyId);

    Optional<Strategy> findByIdempotencyKey(IdempotencyKey idempotencyKey);

    Optional<String> fingerprint(IdempotencyKey idempotencyKey);

    void save(Strategy strategy, IdempotencyKey idempotencyKey, String fingerprint);

}
