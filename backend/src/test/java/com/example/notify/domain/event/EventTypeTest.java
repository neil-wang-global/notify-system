package com.example.notify.domain.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EventTypeTest {

    @Test
    void sameValueIsEqual() {
        assertEquals(new EventType("Order Created"), new EventType("Order Created"));
    }

    @Test
    void differentValueIsNotEqual() {
        assertNotEquals(new EventType("Order Created"), new EventType("Order Cancelled"));
    }

    @Test
    void rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> new EventType(null));
    }

    @Test
    void rejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new EventType("  "));
    }

    @Test
    void trimsAndConvertsToString() {
        assertEquals("Order Created", new EventType(" Order Created ").toString());
    }

}
