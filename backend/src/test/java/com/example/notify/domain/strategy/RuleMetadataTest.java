package com.example.notify.domain.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RuleMetadataTest {

    @Test
    void fixedMetadataContainsSupportedFieldsOnly() {
        assertEquals(RuleValueType.STRING, RuleFieldMetadata.required("customerId").valueType());
        assertEquals(RuleValueType.STRING, RuleFieldMetadata.required("userId").valueType());
        assertEquals(RuleValueType.STRING, RuleFieldMetadata.required("eventType").valueType());
        assertEquals(RuleValueType.STRING, RuleFieldMetadata.required("actionCode").valueType());
        assertEquals(RuleValueType.STRING, RuleFieldMetadata.required("productId").valueType());
        assertEquals(RuleValueType.STRING, RuleFieldMetadata.required("channel").valueType());
        assertEquals(RuleValueType.STRING, RuleFieldMetadata.required("pageCode").valueType());
        assertEquals(RuleValueType.STRING, RuleFieldMetadata.required("source").valueType());
        assertEquals(RuleValueType.DATETIME, RuleFieldMetadata.required("occurredAt").valueType());

        assertFalse(RuleFieldMetadata.supports("attributes.productId"));
        assertThrows(IllegalArgumentException.class, () -> RuleFieldMetadata.required("attributes.productId"));
    }

    @Test
    void operatorCompatibilityMatchesValueTypes() {
        assertTrue(RuleOperator.EQ.supports(RuleValueType.STRING));
        assertTrue(RuleOperator.IN.supports(RuleValueType.STRING_LIST));
        assertTrue(RuleOperator.BETWEEN.supports(RuleValueType.NUMBER_LIST));
        assertTrue(RuleOperator.BETWEEN.supports(RuleValueType.DATETIME));
        assertTrue(RuleOperator.EXISTS.supports(RuleValueType.BOOLEAN));
        assertTrue(RuleOperator.REGEX.supports(RuleValueType.STRING));

        assertFalse(RuleOperator.IN.supports(RuleValueType.STRING));
        assertFalse(RuleOperator.GT.supports(RuleValueType.STRING_LIST));
        assertFalse(RuleOperator.REGEX.supports(RuleValueType.NUMBER));
    }

}
