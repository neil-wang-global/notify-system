package com.example.notify.domain.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class StrategyExecutionPlanTest {

    @Test
    void carriesTimeboxAndBusinessDedupConfiguration() {
        StrategyExecutionPlan plan = new StrategyExecutionPlan(
            Duration.ofMinutes(5),
            Duration.ofSeconds(30),
            Duration.ofSeconds(10),
            List.of("customerId", "userId", "eventType", "productId")
        );

        assertEquals(Duration.ofMinutes(5), plan.windowSize());
        assertEquals(Duration.ofSeconds(30), plan.shardSize());
        assertEquals(Duration.ofSeconds(10), plan.businessDedupWindow());
        assertEquals(List.of("customerId", "userId", "eventType", "productId"), plan.dedupFields());
    }

    @Test
    void rejectsDedupFieldsContainingEventId() {
        assertThrows(IllegalArgumentException.class, () -> new StrategyExecutionPlan(
            Duration.ofMinutes(5),
            Duration.ofSeconds(30),
            Duration.ofSeconds(10),
            List.of("customerId", "eventId")
        ));
    }

}
