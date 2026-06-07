package com.example.notify.engine.matching;

import com.example.notify.domain.strategy.RuleAst;
import com.example.notify.domain.strategy.RuleOperator;
import java.util.List;

public final class RuleAstEvaluator {

    public boolean matches(RuleAst ast, EventSnapshot snapshot) {
        return switch (ast) {
            case RuleAst.Comparison comparison -> compare(comparison, snapshot);
            case RuleAst.Group group -> group.connector() == com.example.notify.domain.strategy.RuleConnector.AND
                ? group.children().stream().allMatch(child -> matches(child, snapshot))
                : group.children().stream().anyMatch(child -> matches(child, snapshot));
            case RuleAst.Not not -> !matches(not.child(), snapshot);
        };
    }

    private boolean compare(RuleAst.Comparison comparison, EventSnapshot snapshot) {
        if (comparison.field().startsWith("attributes.")) {
            throw new IllegalArgumentException("attributes fields are not supported");
        }
        String actual = snapshot.value(comparison.field());
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
            case GT -> compareNumber(actual, comparison.value()) > 0;
            case GTE -> compareNumber(actual, comparison.value()) >= 0;
            case LT -> compareNumber(actual, comparison.value()) < 0;
            case LTE -> compareNumber(actual, comparison.value()) <= 0;
            case BETWEEN -> between(actual, comparison.value());
            case REGEX -> actual.matches(String.valueOf(comparison.value()));
            case EXISTS -> true;
        };
    }

    private static int compareNumber(String actual, Object expected) {
        try {
            return Double.compare(Double.parseDouble(actual), Double.parseDouble(String.valueOf(expected)));
        } catch (NumberFormatException ignored) {
            return Integer.MIN_VALUE;
        }
    }

    private static boolean between(String actual, Object expected) {
        if (!(expected instanceof List<?> values) || values.size() != 2) {
            return false;
        }
        try {
            double actualNumber = Double.parseDouble(actual);
            double lower = Double.parseDouble(String.valueOf(values.get(0)));
            double upper = Double.parseDouble(String.valueOf(values.get(1)));
            return actualNumber >= lower && actualNumber <= upper;
        } catch (NumberFormatException ignored) {
            return actual.compareTo(String.valueOf(values.get(0))) >= 0 && actual.compareTo(String.valueOf(values.get(1))) <= 0;
        }
    }

}
