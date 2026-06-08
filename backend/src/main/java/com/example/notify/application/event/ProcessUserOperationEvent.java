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
import com.example.notify.engine.timebox.TimeboxOperations;
import com.example.notify.engine.timebox.TimeboxResult;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProcessUserOperationEvent {

    private static final Logger log = LoggerFactory.getLogger(ProcessUserOperationEvent.class);

    private final RuleAstEvaluator evaluator;
    private final TimeboxOperations counter;
    private final NotificationEvents notificationEvents;

    public ProcessUserOperationEvent(RuleAstEvaluator evaluator, TimeboxOperations counter, NotificationEvents notificationEvents) {
        this.evaluator = evaluator;
        this.counter = counter;
        this.notificationEvents = notificationEvents;
    }

    public void process(EventId eventId, EventSnapshot snapshot, List<MatchedStrategy> matchedStrategies, Instant occurredAt) {
        int failureCount = 0;
        for (MatchedStrategy matchedStrategy : matchedStrategies) {
            try {
                if (evaluator.matches(matchedStrategy.ruleAst(), snapshot)) {
                    TimeboxResult result = counter.apply(new TimeboxCommand(
                        matchedStrategy.strategyId(),
                        new CustomerId(snapshot.customerId()),
                        dedupDimensionsHash(matchedStrategy.executionPlan(), snapshot),
                        eventId,
                        occurredAt,
                        matchedStrategy.executionPlan().windowSize(),
                        matchedStrategy.executionPlan().shardSize(),
                        matchedStrategy.executionPlan().businessDedupWindow(),
                        matchedStrategy.threshold()
                    ));
                    if (result.triggered()) {
                        notificationEvents.publish(notification(matchedStrategy, eventId, snapshot, occurredAt, result));
                    }
                }
            } catch (Exception e) {
                failureCount++;
                log.error("Failed to process strategy {} for event {}: {}", matchedStrategy.strategyId(), eventId, e.getMessage(), e);
            }
        }
        if (failureCount == matchedStrategies.size() && !matchedStrategies.isEmpty()) {
            throw new IllegalStateException("All strategies failed for event " + eventId);
        }
    }

    private static String dedupDimensionsHash(StrategyExecutionPlan plan, EventSnapshot snapshot) {
        return String.join("", plan.dedupFields().stream().map(field -> encode(snapshot.value(field))).toList());
    }

    private static String encode(String value) {
        if (value == null) {
            return "-1:";
        }
        return value.length() + ":" + value;
    }

    private static NotificationEvent notification(MatchedStrategy matchedStrategy, EventId eventId, EventSnapshot snapshot, Instant occurredAt, TimeboxResult result) {
        String dedupDimensionsHash = dedupDimensionsHash(matchedStrategy.executionPlan(), snapshot);
        String dedupeKey = matchedStrategy.strategyId() + ":" + snapshot.customerId() + ":" + dedupDimensionsHash;
        return new NotificationEvent(
            new NotificationId("notification-" + dedupeKey),
            matchedStrategy.strategyId(),
            new CustomerId(snapshot.customerId()),
            new UserId(snapshot.userId()),
            eventId,
            new EventType(snapshot.eventType()),
            occurredAt,
            matchedStrategy.executionPlan().windowSize().toString(),
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

        public static MatchedStrategy from(com.example.notify.domain.strategy.Strategy strategy) {
            return new MatchedStrategy(strategy.id(), strategy.ruleAst(), strategy.executionPlan(), strategy.threshold());
        }

    }

}
