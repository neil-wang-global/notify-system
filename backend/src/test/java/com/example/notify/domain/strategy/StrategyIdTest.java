package com.example.notify.domain.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class StrategyIdTest {

    @Test
    void sameValueIsEqual() {
        assertEquals(new StrategyId("strategy-1"), new StrategyId("strategy-1"));
    }

    @Test
    void differentValueIsNotEqual() {
        assertNotEquals(new StrategyId("strategy-1"), new StrategyId("strategy-2"));
    }

    @Test
    void rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> new StrategyId(null));
    }

    @Test
    void rejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new StrategyId("  "));
    }

    @Test
    void trimsAndConvertsToString() {
        assertEquals("strategy-1", new StrategyId(" strategy-1 ").toString());
    }

}
