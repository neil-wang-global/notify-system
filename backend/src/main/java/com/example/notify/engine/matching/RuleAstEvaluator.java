package com.example.notify.engine.matching;

import com.example.notify.domain.strategy.RuleAst;
import com.example.notify.domain.strategy.RuleConnector;
import com.example.notify.domain.strategy.RuleOperator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RuleAstEvaluator {

    private static final Logger log = LoggerFactory.getLogger(RuleAstEvaluator.class);

    public boolean matches(RuleAst ast, EventSnapshot snapshot) {
        return switch (ast) {
            case RuleAst.Comparison comparison -> compare(comparison, snapshot);
            case RuleAst.Group group -> group.connector() == RuleConnector.AND
                ? group.children().stream().allMatch(child -> matches(child, snapshot))
                : group.children().stream().anyMatch(child -> matches(child, snapshot));
            case RuleAst.Not not -> !matches(not.child(), snapshot);
        };
    }

    private boolean compare(RuleAst.Comparison comparison, EventSnapshot snapshot) {
        String fieldName = comparison.field();
        if (fieldName.startsWith("attributes.")) {
            fieldName = fieldName.substring("attributes.".length());
        }
        String actual = snapshot.value(fieldName);
        if (comparison.operator() == RuleOperator.EXISTS) {
            return actual != null;
        }
        if (actual == null) {
            return false;
        }
        return switch (comparison.operator()) {
            case EQ -> actual.equals(String.valueOf(comparison.value()));
            case NE -> !actual.equals(String.valueOf(comparison.value()));
            case IN -> comparison.value() instanceof List<?> values && values.stream().anyMatch(value -> actual.equals(String.valueOf(value)));
            case NOT_IN -> !(comparison.value() instanceof List<?> values) || values.stream().noneMatch(value -> actual.equals(String.valueOf(value)));
            case GT -> compareNumber(actual, comparison.value(), comparison) > 0;
            case GTE -> compareNumber(actual, comparison.value(), comparison) >= 0;
            case LT -> compareNumber(actual, comparison.value(), comparison) < 0;
            case LTE -> compareNumber(actual, comparison.value(), comparison) <= 0;
            case BETWEEN -> between(actual, comparison.value(), comparison);
            case REGEX -> actual.matches(String.valueOf(comparison.value()));
            case EXISTS -> true;
        };
    }

    /**
     * T-30: Returns 0 on NumberFormatException instead of Integer.MIN_VALUE.
     * Logs a warning and returns 0, which makes all comparison operators (GT, GTE, LT, LTE)
     * return false consistently (fail-closed policy for non-numeric values).
     */
    private static int compareNumber(String actual, Object expected, RuleAst.Comparison comparison) {
        try {
            return Double.compare(Double.parseDouble(actual), Double.parseDouble(String.valueOf(expected)));
        } catch (NumberFormatException e) {
            log.warn("Cannot compare non-numeric value: field={}, actual={}, expected={}", comparison.field(), actual, expected);
            return 0;
        }
    }

    /**
     * T-31: Consistent fail-closed behavior with compareNumber.
     * On NumberFormatException, logs a warning and returns false instead of
     * falling back to string comparison.
     */
    private static boolean between(String actual, Object expected, RuleAst.Comparison comparison) {
        if (!(expected instanceof List<?> values) || values.size() != 2) {
            return false;
        }
        try {
            double actualNumber = Double.parseDouble(actual);
            double lower = Double.parseDouble(String.valueOf(values.get(0)));
            double upper = Double.parseDouble(String.valueOf(values.get(1)));
            return actualNumber >= lower && actualNumber <= upper;
        } catch (NumberFormatException e) {
            log.warn("Cannot compare non-numeric value in BETWEEN: field={}, actual={}, expected={}", comparison.field(), actual, expected);
            return false;
        }
    }

}
