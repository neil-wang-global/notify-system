package com.example.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.notify.application.event.ProcessUserOperationEvent;
import com.example.notify.domain.notification.NotificationEvent;
import com.example.notify.domain.notification.NotificationEvents;
import com.example.notify.domain.strategy.RuleAst;
import com.example.notify.domain.strategy.RuleOperator;
import com.example.notify.domain.strategy.StrategyExecutionPlan;
import com.example.notify.domain.strategy.StrategyId;
import com.example.notify.engine.matching.RuleAstEvaluator;
import com.example.notify.engine.timebox.TimeboxCounter;
import com.example.notify.interfaces.rest.EventsApi;
import com.example.notify.interfaces.rest.NotificationsApi;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        EventsApi eventsApi = new EventsApi(processor, List.of(strategy));
        NotificationsApi notificationsApi = new NotificationsApi(List::of);

        eventsApi.simulate(request("event-1"));
        eventsApi.simulate(request("event-2"));
        NotificationsApi queryApi = new NotificationsApi(() -> notificationEvents.events.stream()
            .map(event -> new com.example.notify.domain.notification.NotificationRecord(event.notificationId(), event, event.triggeredAt()))
            .toList()
        );

        assertEquals(1, notificationEvents.events.size());
        assertEquals(1, queryApi.list().size());
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

}
