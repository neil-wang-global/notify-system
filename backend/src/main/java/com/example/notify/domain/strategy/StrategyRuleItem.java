package com.example.notify.domain.strategy;

public record StrategyRuleItem(
    String field,
    RuleOperator operator,
    RuleValue value,
    RuleConnector connector,
    RuleGroup group,
    int sortOrder
) {

    public StrategyRuleItem {
        RuleFieldMetadata.required(field);
        if (operator == null) {
            throw new IllegalArgumentException("rule operator must not be null");
        }
        if (value == null) {
            throw new IllegalArgumentException("rule value must not be null");
        }
        if (connector == null) {
            throw new IllegalArgumentException("rule connector must not be null");
        }
        if (group == null) {
            throw new IllegalArgumentException("rule group must not be null");
        }
        if (!operator.supports(value.type())) {
            throw new IllegalArgumentException("rule operator does not support value type");
        }
        field = field.trim();
    }

    public static StrategyRuleItem condition(
        String field,
        RuleOperator operator,
        RuleValue value,
        RuleConnector connector,
        RuleGroup group,
        int sortOrder
    ) {
        return new StrategyRuleItem(field, operator, value, connector, group, sortOrder);
    }

}
