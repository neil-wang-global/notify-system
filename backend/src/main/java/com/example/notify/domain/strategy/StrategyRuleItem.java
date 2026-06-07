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
        RuleFieldMetadata metadata = RuleFieldMetadata.required(field);
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
        if (sortOrder <= 0) {
            throw new IllegalArgumentException("rule sort order must be positive");
        }
        if (!operator.supports(value.type())) {
            throw new IllegalArgumentException("rule operator does not support value type");
        }
        if (!fieldSupports(metadata.valueType(), value.type())) {
            throw new IllegalArgumentException("rule field does not support value type");
        }
        if (operator == RuleOperator.BETWEEN && !(value.raw() instanceof java.util.List<?> values && values.size() == 2)) {
            throw new IllegalArgumentException("between rule value must contain exactly two values");
        }
        if (operator != RuleOperator.BETWEEN && operator != RuleOperator.IN && operator != RuleOperator.NOT_IN && value.raw() instanceof java.util.List<?>) {
            throw new IllegalArgumentException("scalar operator cannot use range value");
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

    private static boolean fieldSupports(RuleValueType fieldType, RuleValueType valueType) {
        return fieldType == valueType
            || fieldType == RuleValueType.STRING && valueType == RuleValueType.STRING_LIST
            || fieldType == RuleValueType.NUMBER && valueType == RuleValueType.NUMBER_LIST;
    }

}
