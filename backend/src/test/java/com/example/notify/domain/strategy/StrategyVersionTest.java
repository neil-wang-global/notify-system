package com.example.notify.domain.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class StrategyVersionTest {

    @Test
    void sameValueIsEqual() {
        assertEquals(new StrategyVersion(1), new StrategyVersion(1));
    }

    @Test
    void differentValueIsNotEqual() {
        assertNotEquals(new StrategyVersion(1), new StrategyVersion(2));
    }

    @Test
    void rejectsZero() {
        assertThrows(IllegalArgumentException.class, () -> new StrategyVersion(0));
    }

    @Test
    void rejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> new StrategyVersion(-1));
    }

    @Test
    void convertsToString() {
        assertEquals("1", new StrategyVersion(1).toString());
    }

}
