package com.example.notify.domain.strategy;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public sealed interface RuleAst permits RuleAst.Comparison, RuleAst.Group, RuleAst.Not {

    static RuleAst fromRows(List<StrategyRuleItem> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new IllegalArgumentException("rule rows must not be empty");
        }

        List<StrategyRuleItem> sortedRows = rows.stream()
            .sorted(Comparator.comparingInt(StrategyRuleItem::sortOrder))
            .toList();
        Map<RuleGroup, List<StrategyRuleItem>> rowsByGroup = sortedRows.stream()
            .collect(Collectors.groupingBy(StrategyRuleItem::group, java.util.LinkedHashMap::new, Collectors.toList()));

        if (rowsByGroup.size() == 1) {
            return buildGroup(rowsByGroup.values().iterator().next());
        }

        List<RuleAst> children = rowsByGroup.values().stream()
            .map(RuleAst::buildGroup)
            .toList();
        return new Group(RuleConnector.AND, flattenSingleComparison(children));
    }

    private static RuleAst buildGroup(List<StrategyRuleItem> groupRows) {
        if (groupRows.size() == 1) {
            return comparisonFor(groupRows.getFirst());
        }
        RuleConnector connector = groupRows.getFirst().connector();
        List<RuleAst> children = groupRows.stream()
            .map(RuleAst::comparisonFor)
            .toList();
        return new Group(connector, children);
    }

    private static List<RuleAst> flattenSingleComparison(List<RuleAst> children) {
        return children.stream()
            .flatMap(child -> {
                if (child instanceof Group group && group.connector() == RuleConnector.AND) {
                    return group.children().stream();
                }
                return java.util.stream.Stream.of(child);
            })
            .toList();
    }

    private static RuleAst comparisonFor(StrategyRuleItem item) {
        if (item.operator() == RuleOperator.NOT_IN) {
            return new Not(new Comparison(item.field(), RuleOperator.IN, item.value().raw()));
        }
        return new Comparison(item.field(), item.operator(), item.value().raw());
    }

    record Comparison(String field, RuleOperator operator, Object value) implements RuleAst {

        public Comparison {
            if (field == null || field.isBlank()) {
                throw new IllegalArgumentException("comparison field must not be blank");
            }
            if (operator == null) {
                throw new IllegalArgumentException("comparison operator must not be null");
            }
            if (value == null) {
                throw new IllegalArgumentException("comparison value must not be null");
            }
            field = field.trim();
        }

    }

    record Group(RuleConnector connector, List<RuleAst> children) implements RuleAst {

        public Group {
            if (connector == null) {
                throw new IllegalArgumentException("group connector must not be null");
            }
            if (children == null || children.isEmpty()) {
                throw new IllegalArgumentException("group children must not be empty");
            }
            children = List.copyOf(children);
        }

    }

    record Not(RuleAst child) implements RuleAst {

        public Not {
            if (child == null) {
                throw new IllegalArgumentException("not child must not be null");
            }
        }

    }

}
