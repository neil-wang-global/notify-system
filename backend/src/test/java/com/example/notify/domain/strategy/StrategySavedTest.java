package com.example.notify.domain.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class StrategySavedTest {

    @Test
    void keepsStrategyIdVersionAndOccurredAt() {
        StrategyId strategyId = new StrategyId("strategy-1");
        StrategyVersion version = new StrategyVersion(1);
        Instant occurredAt = Instant.parse("2026-06-07T10:15:30Z");

        StrategySaved event = new StrategySaved(strategyId, version, occurredAt);

        assertEquals(strategyId, event.strategyId());
        assertEquals(version, event.version());
        assertEquals(occurredAt, event.occurredAt());
    }

    @Test
    void rejectsNullStrategyId() {
        assertThrows(IllegalArgumentException.class,
                () -> new StrategySaved(null, new StrategyVersion(1), Instant.parse("2026-06-07T10:15:30Z")));
    }

    @Test
    void rejectsNullVersion() {
        assertThrows(IllegalArgumentException.class,
                () -> new StrategySaved(new StrategyId("strategy-1"), null, Instant.parse("2026-06-07T10:15:30Z")));
    }

    @Test
    void rejectsNullOccurredAt() {
        assertThrows(IllegalArgumentException.class,
                () -> new StrategySaved(new StrategyId("strategy-1"), new StrategyVersion(1), null));
    }

    @Test
    void sameFieldsAreEqual() {
        Instant occurredAt = Instant.parse("2026-06-07T10:15:30Z");

        assertEquals(
                new StrategySaved(new StrategyId("strategy-1"), new StrategyVersion(1), occurredAt),
                new StrategySaved(new StrategyId("strategy-1"), new StrategyVersion(1), occurredAt));
    }

}
