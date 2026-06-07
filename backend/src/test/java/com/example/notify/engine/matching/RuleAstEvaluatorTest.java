package com.example.notify.engine.matching;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.notify.domain.strategy.RuleAst;
import com.example.notify.domain.strategy.RuleConnector;
import com.example.notify.domain.strategy.RuleOperator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RuleAstEvaluatorTest {

    @Test
    void evaluatesRequiredOperators() {
        EventSnapshot snapshot = new EventSnapshot("customer-1", "user-1", Set.of(), "PRODUCT_VIEW", Map.of(
            "productId", "P001",
            "channel", "APP",
            "count", "6",
            "source", "MINI_PROGRAM"
        ));
        RuleAst ast = new RuleAst.Group(RuleConnector.AND, List.of(
            new RuleAst.Comparison("eventType", RuleOperator.EQ, "PRODUCT_VIEW"),
            new RuleAst.Comparison("productId", RuleOperator.IN, List.of("P001", "P002")),
            new RuleAst.Comparison("count", RuleOperator.GTE, 5),
            new RuleAst.Not(new RuleAst.Comparison("channel", RuleOperator.EQ, "WEB")),
            new RuleAst.Group(RuleConnector.OR, List.of(
                new RuleAst.Comparison("source", RuleOperator.EQ, "APP"),
                new RuleAst.Comparison("source", RuleOperator.EQ, "MINI_PROGRAM")
            ))
        ));

        assertTrue(new RuleAstEvaluator().matches(ast, snapshot));
    }

    @Test
    void rejectsAttributesFields() {
        EventSnapshot snapshot = new EventSnapshot("customer-1", "user-1", Set.of(), "PRODUCT_VIEW", Map.of("attributes.productId", "P001"));

        assertThrows(IllegalArgumentException.class, () -> new RuleAstEvaluator().matches(
            new RuleAst.Comparison("attributes.productId", RuleOperator.EQ, "P001"),
            snapshot
        ));
    }

    @Test
    void returnsFalseForTypeMismatch() {
        EventSnapshot snapshot = new EventSnapshot("customer-1", "user-1", Set.of(), "PRODUCT_VIEW", Map.of("count", "abc"));

        assertFalse(new RuleAstEvaluator().matches(new RuleAst.Comparison("count", RuleOperator.GT, 5), snapshot));
    }

}
