package com.example.notify.application.port;

import com.example.notify.domain.strategy.StrategyId;
import com.example.notify.domain.strategy.StrategyVersion;

/**
 * Port for cache invalidation when a strategy is saved.
 * Implemented by the infrastructure caching layer.
 */
public interface CacheInvalidationPort {
    void invalidate(StrategyId strategyId, StrategyVersion version);
}
