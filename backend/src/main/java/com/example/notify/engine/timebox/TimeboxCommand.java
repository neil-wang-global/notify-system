package com.example.notify.engine.timebox;

import com.example.notify.domain.event.CustomerId;
import com.example.notify.domain.event.EventId;
import com.example.notify.domain.strategy.StrategyId;
import java.time.Duration;
import java.time.Instant;

public record TimeboxCommand(
    StrategyId strategyId,
    CustomerId customerId,
    String dedupDimensionsHash,
    EventId eventId,
    Instant occurredAt,
    Duration windowSize,
    Duration shardSize,
    Duration businessDedupWindow,
    int threshold
) {

    public TimeboxCommand {
        if (strategyId == null || customerId == null || dedupDimensionsHash == null || dedupDimensionsHash.isBlank() || eventId == null || occurredAt == null) {
            throw new IllegalArgumentException("timebox command identity is incomplete");
        }
        if (windowSize == null || shardSize == null || businessDedupWindow == null || threshold < 1) {
            throw new IllegalArgumentException("timebox command configuration is incomplete");
        }
        dedupDimensionsHash = dedupDimensionsHash.trim();
    }

    public String windowKey() {
        return strategyId + ":" + customerId;
    }

    public String dedupKey() {
        return strategyId + ":" + customerId + ":" + dedupDimensionsHash;
    }

}
