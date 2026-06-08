package com.example.notify.infrastructure.redis;

import com.example.notify.domain.event.EventType;
import com.example.notify.domain.strategy.RuleAst;
import com.example.notify.domain.strategy.Strategy;
import com.example.notify.domain.strategy.StrategyExecutionPlan;
import com.example.notify.domain.strategy.StrategyId;
import com.example.notify.domain.strategy.StrategyScope;
import com.example.notify.domain.strategy.StrategyVersion;
import java.util.ArrayList;
import java.util.List;

public record RedisStrategy(
    StrategyId strategyId, StrategyVersion version, StrategyExecutionPlan executionPlan,
    StrategyScope scope, EventType eventType, List<RedisFieldIndex> fieldIndexes
) {
    public RedisStrategy {
        if (strategyId == null || version == null || executionPlan == null || scope == null || eventType == null || fieldIndexes == null) {
            throw new IllegalArgumentException("redis strategy is incomplete");
        }
        fieldIndexes = List.copyOf(fieldIndexes);
    }

    public static RedisStrategy from(Strategy strategy) {
        if (strategy == null) { throw new IllegalArgumentException("strategy must not be null"); }
        EventType extracted = extractEventType(strategy.ruleAst());
        if (extracted == null) { extracted = new EventType("UNKNOWN"); }
        return new RedisStrategy(strategy.id(), strategy.version(), strategy.executionPlan(),
            strategy.scope(), extracted, extractFieldIndexes(strategy.ruleAst()));
    }

    private static EventType extractEventType(RuleAst ast) {
        return switch (ast) {
            case RuleAst.Comparison c when "eventType".equals(c.field()) ->
                new EventType(String.valueOf(c.value()));
            case RuleAst.Comparison c ->
                null;
            case RuleAst.Group g -> {
                EventType result = null;
                for (RuleAst child : g.children()) {
                    result = extractEventType(child);
                    if (result != null) break;
                }
                yield result;
            }
            case RuleAst.Not n -> extractEventType(n.child());
        };
    }

    private static List<RedisFieldIndex> extractFieldIndexes(RuleAst ast) {
        List<RedisFieldIndex> indexes = new ArrayList<>();
        collectFieldIndexes(ast, indexes);
        return indexes;
    }

    private static void collectFieldIndexes(RuleAst ast, List<RedisFieldIndex> indexes) {
        switch (ast) {
            case RuleAst.Comparison c -> {
                String f = c.field();
                if (!"eventType".equals(f) && !"customerId".equals(f) && !"userId".equals(f))
                    indexes.add(new RedisFieldIndex(f, String.valueOf(c.value())));
            }
            case RuleAst.Group g -> g.children().forEach(child -> collectFieldIndexes(child, indexes));
            case RuleAst.Not n -> collectFieldIndexes(n.child(), indexes);
        }
    }

}
