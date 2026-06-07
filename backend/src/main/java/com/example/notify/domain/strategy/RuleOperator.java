package com.example.notify.domain.strategy;

import java.util.EnumSet;
import java.util.Set;

public enum RuleOperator {
    EQ(RuleValueType.STRING, RuleValueType.NUMBER, RuleValueType.BOOLEAN, RuleValueType.DATETIME),
    NE(RuleValueType.STRING, RuleValueType.NUMBER, RuleValueType.BOOLEAN, RuleValueType.DATETIME),
    IN(RuleValueType.STRING_LIST, RuleValueType.NUMBER_LIST),
    NOT_IN(RuleValueType.STRING_LIST, RuleValueType.NUMBER_LIST),
    GT(RuleValueType.NUMBER, RuleValueType.DATETIME),
    GTE(RuleValueType.NUMBER, RuleValueType.DATETIME),
    LT(RuleValueType.NUMBER, RuleValueType.DATETIME),
    LTE(RuleValueType.NUMBER, RuleValueType.DATETIME),
    BETWEEN(RuleValueType.NUMBER_LIST, RuleValueType.DATETIME),
    EXISTS(RuleValueType.BOOLEAN),
    REGEX(RuleValueType.STRING);

    private final Set<RuleValueType> supportedTypes;

    RuleOperator(RuleValueType firstSupportedType, RuleValueType... otherSupportedTypes) {
        this.supportedTypes = EnumSet.of(firstSupportedType, otherSupportedTypes);
    }

    public boolean supports(RuleValueType valueType) {
        return supportedTypes.contains(valueType);
    }

}
