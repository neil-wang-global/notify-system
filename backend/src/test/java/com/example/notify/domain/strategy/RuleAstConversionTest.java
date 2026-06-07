package com.example.notify.domain.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class RuleAstConversionTest {

    @Test
    void convertsSingleRuleRowToComparisonAst() {
        RuleAst ast = RuleAst.fromRows(List.of(StrategyRuleItem.condition(
            "eventType",
            RuleOperator.EQ,
            RuleValue.ofString("PRODUCT_VIEW"),
            RuleConnector.AND,
            new RuleGroup("root"),
            1
        )));

        assertEquals(new RuleAst.Comparison("eventType", RuleOperator.EQ, "PRODUCT_VIEW"), ast);
    }

    @Test
    void convertsAndOrGroupsToNestedAst() {
        RuleAst ast = RuleAst.fromRows(List.of(
            StrategyRuleItem.condition("eventType", RuleOperator.EQ, RuleValue.ofString("PRODUCT_VIEW"), RuleConnector.AND, new RuleGroup("root"), 1),
            StrategyRuleItem.condition("productId", RuleOperator.IN, RuleValue.ofStrings("P001", "P002"), RuleConnector.AND, new RuleGroup("root"), 2),
            StrategyRuleItem.condition("channel", RuleOperator.EQ, RuleValue.ofString("APP"), RuleConnector.OR, new RuleGroup("channel-source"), 3),
            StrategyRuleItem.condition("source", RuleOperator.EQ, RuleValue.ofString("MINI_PROGRAM"), RuleConnector.OR, new RuleGroup("channel-source"), 4)
        ));

        assertEquals(new RuleAst.Group(RuleConnector.AND, List.of(
            new RuleAst.Comparison("eventType", RuleOperator.EQ, "PRODUCT_VIEW"),
            new RuleAst.Comparison("productId", RuleOperator.IN, List.of("P001", "P002")),
            new RuleAst.Group(RuleConnector.OR, List.of(
                new RuleAst.Comparison("channel", RuleOperator.EQ, "APP"),
                new RuleAst.Comparison("source", RuleOperator.EQ, "MINI_PROGRAM")
            ))
        )), ast);
    }

    @Test
    void convertsNotRowToNotAst() {
        RuleAst ast = RuleAst.fromRows(List.of(StrategyRuleItem.condition(
            "channel",
            RuleOperator.NOT_IN,
            RuleValue.ofStrings("WEB", "H5"),
            RuleConnector.AND,
            new RuleGroup("root"),
            1
        )));

        assertEquals(new RuleAst.Not(new RuleAst.Comparison("channel", RuleOperator.IN, List.of("WEB", "H5"))), ast);
    }

    @Test
    void supportsBetweenAstValues() {
        RuleAst ast = RuleAst.fromRows(List.of(StrategyRuleItem.condition(
            "occurredAt",
            RuleOperator.BETWEEN,
            RuleValue.ofDatetimes("2026-06-07T00:00:00Z", "2026-06-08T00:00:00Z"),
            RuleConnector.AND,
            new RuleGroup("root"),
            1
        )));

        assertEquals(new RuleAst.Comparison("occurredAt", RuleOperator.BETWEEN, List.of("2026-06-07T00:00:00Z", "2026-06-08T00:00:00Z")), ast);
    }

    @Test
    void rejectsEmptyRows() {
        assertThrows(IllegalArgumentException.class, () -> RuleAst.fromRows(List.of()));
    }

    @Test
    void rejectsMixedConnectorsInsideSameGroup() {
        assertThrows(IllegalArgumentException.class, () -> RuleAst.fromRows(List.of(
            StrategyRuleItem.condition("eventType", RuleOperator.EQ, RuleValue.ofString("PRODUCT_VIEW"), RuleConnector.AND, new RuleGroup("root"), 1),
            StrategyRuleItem.condition("productId", RuleOperator.EQ, RuleValue.ofString("P001"), RuleConnector.OR, new RuleGroup("root"), 2)
        )));
    }

}
