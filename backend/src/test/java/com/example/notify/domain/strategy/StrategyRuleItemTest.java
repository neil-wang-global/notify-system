package com.example.notify.domain.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class StrategyRuleItemTest {

    @Test
    void createsValidRuleItem() {
        StrategyRuleItem item = StrategyRuleItem.condition(
            "productId",
            RuleOperator.IN,
            RuleValue.ofStrings("P001", "P002"),
            RuleConnector.AND,
            new RuleGroup("group-1"),
            10
        );

        assertEquals("productId", item.field());
        assertEquals(RuleOperator.IN, item.operator());
        assertEquals(List.of("P001", "P002"), item.value().raw());
        assertEquals(RuleConnector.AND, item.connector());
        assertEquals(new RuleGroup("group-1"), item.group());
        assertEquals(10, item.sortOrder());
    }

    @Test
    void rejectsAttributesFields() {
        assertThrows(IllegalArgumentException.class, () -> StrategyRuleItem.condition(
            "attributes.productId",
            RuleOperator.EQ,
            RuleValue.ofString("P001"),
            RuleConnector.AND,
            new RuleGroup("group-1"),
            1
        ));
    }

    @Test
    void rejectsOperatorValueTypeMismatch() {
        assertThrows(IllegalArgumentException.class, () -> StrategyRuleItem.condition(
            "productId",
            RuleOperator.IN,
            RuleValue.ofString("P001"),
            RuleConnector.AND,
            new RuleGroup("group-1"),
            1
        ));
    }

    @Test
    void rejectsFieldMetadataValueTypeMismatch() {
        assertThrows(IllegalArgumentException.class, () -> StrategyRuleItem.condition(
            "occurredAt",
            RuleOperator.EQ,
            RuleValue.ofString("2026-06-07T00:00:00Z"),
            RuleConnector.AND,
            new RuleGroup("group-1"),
            1
        ));
    }

    @Test
    void rejectsBetweenWithSingleDatetime() {
        assertThrows(IllegalArgumentException.class, () -> StrategyRuleItem.condition(
            "occurredAt",
            RuleOperator.BETWEEN,
            RuleValue.ofDatetime("2026-06-07T00:00:00Z"),
            RuleConnector.AND,
            new RuleGroup("group-1"),
            1
        ));
    }

    @Test
    void rejectsScalarOperatorWithDatetimeRange() {
        assertThrows(IllegalArgumentException.class, () -> StrategyRuleItem.condition(
            "occurredAt",
            RuleOperator.EQ,
            RuleValue.ofDatetimes("2026-06-07T00:00:00Z", "2026-06-08T00:00:00Z"),
            RuleConnector.AND,
            new RuleGroup("group-1"),
            1
        ));
    }

    @Test
    void rejectsNonPositiveSortOrder() {
        assertThrows(IllegalArgumentException.class, () -> StrategyRuleItem.condition(
            "productId",
            RuleOperator.EQ,
            RuleValue.ofString("P001"),
            RuleConnector.AND,
            new RuleGroup("group-1"),
            0
        ));
    }

}
