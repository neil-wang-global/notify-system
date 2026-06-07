package com.example.notify.application.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.notify.domain.event.EventId;
import com.example.notify.domain.event.EventType;
import com.example.notify.domain.notification.NotificationEvent;
import com.example.notify.domain.notification.NotificationEvents;
import com.example.notify.domain.strategy.RuleAst;
import com.example.notify.domain.strategy.RuleOperator;
import com.example.notify.domain.strategy.StrategyExecutionPlan;
import com.example.notify.domain.strategy.StrategyId;
import com.example.notify.engine.matching.EventSnapshot;
import com.example.notify.engine.matching.RuleAstEvaluator;
import com.example.notify.engine.timebox.TimeboxCounter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProcessUserOperationEventTest {

    @Test
    void publishesNotificationForEveryMatchedStrategyWithoutCooldown() {
        CapturingNotificationEvents notifications = new CapturingNotificationEvents();
        ProcessUserOperationEvent process = new ProcessUserOperationEvent(new RuleAstEvaluator(), new TimeboxCounter(), notifications);
        EventSnapshot snapshot = new EventSnapshot("customer-1", "user-1", Set.of(), "PRODUCT_VIEW", Map.of("productId", "P001"));
        List<ProcessUserOperationEvent.MatchedStrategy> matchedStrategies = List.of(
            new ProcessUserOperationEvent.MatchedStrategy(new StrategyId("strategy-1"), new RuleAst.Comparison("productId", RuleOperator.EQ, "P001"), new StrategyExecutionPlan("plan-1"), 1),
            new ProcessUserOperationEvent.MatchedStrategy(new StrategyId("strategy-2"), new RuleAst.Comparison("eventType", RuleOperator.EQ, "PRODUCT_VIEW"), new StrategyExecutionPlan("plan-2"), 1)
        );

        process.process(new EventId("event-1"), snapshot, matchedStrategies, Instant.parse("2026-06-07T00:00:00Z"));
        process.process(new EventId("event-2"), snapshot, matchedStrategies, Instant.parse("2026-06-07T00:00:11Z"));

        assertEquals(4, notifications.events.size());
    }

    private static final class CapturingNotificationEvents implements NotificationEvents {
        private final List<NotificationEvent> events = new ArrayList<>();

        @Override
        public void publish(NotificationEvent event) {
            events.add(event);
        }
    }

}
