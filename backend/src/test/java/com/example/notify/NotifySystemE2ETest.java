package com.example.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.notify.application.event.ProcessUserOperationEvent;
import com.example.notify.domain.notification.NotificationEvent;
import com.example.notify.domain.notification.NotificationEvents;
import com.example.notify.domain.strategy.RuleAst;
import com.example.notify.domain.strategy.RuleConnector;
import com.example.notify.domain.strategy.RuleOperator;
import com.example.notify.domain.strategy.StrategyExecutionPlan;
import com.example.notify.domain.strategy.StrategyId;
import com.example.notify.domain.strategy.Strategies;
import com.example.notify.domain.strategy.Strategy;
import com.example.notify.domain.strategy.StrategyName;
import com.example.notify.domain.strategy.StrategyScope;
import com.example.notify.domain.strategy.StrategyVersion;
import com.example.notify.infrastructure.redis.CandidateStrategyLookup;
import com.example.notify.infrastructure.redis.RedisStrategies;
import com.example.notify.engine.matching.RuleAstEvaluator;
import com.example.notify.engine.timebox.TimeboxCounter;
import com.example.notify.interfaces.rest.EventsApi;
import com.example.notify.interfaces.rest.NotificationsApi;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NotifySystemE2ETest {

    @Test
    void simulatedEventsTriggerNotificationsThatCanBeQueried() {
        CapturingNotificationEvents notificationEvents = new CapturingNotificationEvents();
        ProcessUserOperationEvent processor = new ProcessUserOperationEvent(new RuleAstEvaluator(), new TimeboxCounter(), notificationEvents);
        ProcessUserOperationEvent.MatchedStrategy strategy = new ProcessUserOperationEvent.MatchedStrategy(
            new StrategyId("strategy-1"),
            new RuleAst.Comparison("productId", RuleOperator.EQ, "P001"),
            new StrategyExecutionPlan(Duration.ofSeconds(30), Duration.ofSeconds(10), Duration.ZERO, List.of("customerId", "userId", "eventType", "productId")),
            2
        );

        RedisStrategies candidateLookup = new RedisStrategies();
        RuleAst ast = new RuleAst.Group(RuleConnector.AND, List.of(
            new RuleAst.Comparison("eventType", RuleOperator.EQ, "PRODUCT_VIEW"),
            new RuleAst.Comparison("productId", RuleOperator.EQ, "P001")));
        Strategy domainStrategy = new Strategy(new StrategyId("strategy-1"), new StrategyName("test"),
            StrategyScope.global(), ast,
            new StrategyExecutionPlan(Duration.ofSeconds(30), Duration.ofSeconds(10), Duration.ZERO, List.of("customerId", "userId", "eventType", "productId")),
            new StrategyVersion(1));
        candidateLookup.refresh(domainStrategy);

        Strategies strategies = new InMemoryStrategies();
        strategies.save(domainStrategy, new com.example.notify.domain.strategy.IdempotencyKey("test-key"), "fp");

        EventsApi eventsApi = new EventsApi(processor, candidateLookup, strategies);
        NotificationsApi notificationsApi = new NotificationsApi(List::of);

        eventsApi.simulate(request("event-1"));
        eventsApi.simulate(request("event-2"));
        NotificationsApi queryApi = new NotificationsApi(() -> notificationEvents.events.stream()
            .map(event -> new com.example.notify.domain.notification.NotificationRecord(event.notificationId(), event, event.triggeredAt()))
            .toList()
        );

        assertEquals(2, notificationEvents.events.size());
        assertEquals(2, queryApi.list().size());
    }

    private static EventsApi.UserOperationEventRequest request(String eventId) {
        return new EventsApi.UserOperationEventRequest(
            eventId,
            "customer-1",
            "user-1",
            List.of(),
            "PRODUCT_VIEW",
            Map.of("productId", "P001"),
            Instant.parse("2026-06-07T00:00:00Z")
        );
    }

    private static final class CapturingNotificationEvents implements NotificationEvents {
        private final List<NotificationEvent> events = new ArrayList<>();

        @Override
        public void publish(NotificationEvent event) {
            events.add(event);
        }
    }

    private static final class InMemoryStrategies implements Strategies {
        private final java.util.Map<StrategyId, Strategy> store = new java.util.HashMap<>();

        @Override public List<Strategy> list() { return List.copyOf(store.values()); }
        @Override public Optional<Strategy> find(StrategyId id) { return Optional.ofNullable(store.get(id)); }
        @Override public Optional<Strategy> findByIdempotencyKey(com.example.notify.domain.strategy.IdempotencyKey key) { return Optional.empty(); }
        @Override public Optional<String> fingerprint(com.example.notify.domain.strategy.IdempotencyKey key) { return Optional.empty(); }
        @Override public void save(Strategy s, com.example.notify.domain.strategy.IdempotencyKey key, String fp) { store.put(s.id(), s); }
    }

}
