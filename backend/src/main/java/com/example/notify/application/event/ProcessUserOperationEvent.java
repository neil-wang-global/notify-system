package com.example.notify.application.event;

import com.example.notify.domain.event.CustomerId;
import com.example.notify.domain.event.EventId;
import com.example.notify.domain.event.EventType;
import com.example.notify.domain.event.UserId;
import com.example.notify.domain.notification.NotificationEvent;
import com.example.notify.domain.notification.NotificationEvents;
import com.example.notify.domain.notification.NotificationId;
import com.example.notify.domain.strategy.RuleAst;
import com.example.notify.domain.strategy.StrategyExecutionPlan;
import com.example.notify.domain.strategy.StrategyId;
import com.example.notify.engine.matching.EventSnapshot;
import com.example.notify.engine.matching.RuleAstEvaluator;
import com.example.notify.engine.timebox.TimeboxCommand;
import com.example.notify.engine.timebox.TimeboxCounter;
import com.example.notify.engine.timebox.TimeboxResult;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class ProcessUserOperationEvent {

    private final RuleAstEvaluator evaluator;
    private final TimeboxCounter counter;
    private final NotificationEvents notificationEvents;

    public ProcessUserOperationEvent(RuleAstEvaluator evaluator, TimeboxCounter counter, NotificationEvents notificationEvents) {
        this.evaluator = evaluator;
        this.counter = counter;
        this.notificationEvents = notificationEvents;
    }

    public void process(EventId eventId, EventSnapshot snapshot, List<MatchedStrategy> matchedStrategies, Instant occurredAt) {
        for (MatchedStrategy matchedStrategy : matchedStrategies) {
            if (evaluator.matches(matchedStrategy.ruleAst(), snapshot)) {
                TimeboxResult result = counter.apply(new TimeboxCommand(
                    matchedStrategy.strategyId(),
                    new CustomerId(snapshot.customerId()),
                    snapshot.customerId() + ':' + snapshot.userId() + ':' + snapshot.eventType() + ':' + eventId,
                    eventId,
                    occurredAt,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(10),
                    Duration.ZERO,
                    matchedStrategy.threshold()
                ));
                if (result.triggered()) {
                    notificationEvents.publish(notification(matchedStrategy, eventId, snapshot, occurredAt, result));
                }
            }
        }
    }

    private static NotificationEvent notification(MatchedStrategy matchedStrategy, EventId eventId, EventSnapshot snapshot, Instant occurredAt, TimeboxResult result) {
        String dedupeKey = matchedStrategy.strategyId() + ":" + eventId;
        return new NotificationEvent(
            new NotificationId("notification-" + dedupeKey),
            matchedStrategy.strategyId(),
            new CustomerId(snapshot.customerId()),
            new UserId(snapshot.userId()),
            eventId,
            new EventType(snapshot.eventType()),
            occurredAt,
            "30s",
            matchedStrategy.threshold(),
            result.currentCount(),
            dedupeKey
        );
    }

    public record MatchedStrategy(StrategyId strategyId, RuleAst ruleAst, StrategyExecutionPlan executionPlan, int threshold) {

        public MatchedStrategy {
            if (strategyId == null || ruleAst == null || executionPlan == null || threshold < 1) {
                throw new IllegalArgumentException("matched strategy is incomplete");
            }
        }

    }

}
