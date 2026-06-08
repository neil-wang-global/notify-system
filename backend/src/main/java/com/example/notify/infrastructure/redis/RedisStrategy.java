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
        return new RedisStrategy(strategy.id(), strategy.version(), strategy.executionPlan(),
            strategy.scope(), extractEventType(strategy.ruleAst()), extractFieldIndexes(strategy.ruleAst()));
    }

    private static EventType extractEventType(RuleAst ast) {
        RuleAst.Comparison first = firstComparison(ast);
        return "eventType".equals(first.field()) ? new EventType(String.valueOf(first.value())) : new EventType("UNKNOWN");
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

    private static RuleAst.Comparison firstComparison(RuleAst ast) {
        return switch (ast) {
            case RuleAst.Comparison c -> c;
            case RuleAst.Group g -> firstComparison(g.children().getFirst());
            case RuleAst.Not n -> firstComparison(n.child());
        };
    }
}
