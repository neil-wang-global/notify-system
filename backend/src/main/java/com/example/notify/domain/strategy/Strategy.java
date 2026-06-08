package com.example.notify.domain.strategy;

public record Strategy(
    StrategyId id,
    StrategyName name,
    StrategyScope scope,
    RuleAst ruleAst,
    StrategyExecutionPlan executionPlan,
    int threshold,
    StrategyVersion version
) {

    public Strategy {
        if (id == null) {
            throw new IllegalArgumentException("strategyId must not be null");
        }
        if (name == null) {
            throw new IllegalArgumentException("strategyName must not be null");
        }
        if (scope == null) {
            throw new IllegalArgumentException("strategyScope must not be null");
        }
        if (ruleAst == null) {
            throw new IllegalArgumentException("ruleAst must not be null");
        }
        if (executionPlan == null) {
            throw new IllegalArgumentException("strategyExecutionPlan must not be null");
        }
        if (threshold < 1) {
            throw new IllegalArgumentException("threshold must be at least 1");
        }
        if (version == null) {
            throw new IllegalArgumentException("strategyVersion must not be null");
        }
    }

    public static Strategy create(
        StrategyId id,
        StrategyName name,
        StrategyScope scope,
        RuleAst ruleAst,
        StrategyExecutionPlan executionPlan,
        int threshold
    ) {
        return new Strategy(id, name, scope, ruleAst, executionPlan, threshold, new StrategyVersion(1));
    }

    public Strategy update(
        StrategyName name,
        StrategyScope scope,
        RuleAst ruleAst,
        StrategyExecutionPlan executionPlan,
        int threshold
    ) {
        return new Strategy(id, name, scope, ruleAst, executionPlan, threshold, version.next());
    }

}
