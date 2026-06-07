package com.example.notify.domain.strategy;

import java.time.Duration;
import java.util.List;

public record StrategyExecutionPlan(Duration windowSize, Duration shardSize, Duration businessDedupWindow, List<String> dedupFields) {

    public StrategyExecutionPlan(String ignored) {
        this(Duration.ofSeconds(30), Duration.ofSeconds(10), Duration.ZERO, List.of("customerId", "userId", "eventType"));
    }

    public StrategyExecutionPlan {
        if (windowSize == null || shardSize == null || businessDedupWindow == null || dedupFields == null || dedupFields.isEmpty()) {
            throw new IllegalArgumentException("strategy execution plan is incomplete");
        }
        if (windowSize.isZero() || windowSize.isNegative() || shardSize.isZero() || shardSize.isNegative() || businessDedupWindow.isNegative()) {
            throw new IllegalArgumentException("strategy execution plan durations are invalid");
        }
        dedupFields = dedupFields.stream().map(StrategyExecutionPlan::normalizeField).toList();
        if (dedupFields.contains("eventId")) {
            throw new IllegalArgumentException("eventId must not be a business dedup field");
        }
    }

    @Override
    public String toString() {
        return windowSize + ":" + shardSize + ":" + businessDedupWindow + ":" + dedupFields;
    }

    private static String normalizeField(String field) {
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("dedup field must not be blank");
        }
        return field.trim();
    }

}
