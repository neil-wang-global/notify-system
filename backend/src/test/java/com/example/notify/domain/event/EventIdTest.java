package com.example.notify.domain.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EventIdTest {

    @Test
    void sameValueIsEqual() {
        assertEquals(new EventId("event-1"), new EventId("event-1"));
    }

    @Test
    void differentValueIsNotEqual() {
        assertNotEquals(new EventId("event-1"), new EventId("event-2"));
    }

    @Test
    void rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> new EventId(null));
    }

    @Test
    void rejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new EventId("  "));
    }

    @Test
    void trimsAndConvertsToString() {
        assertEquals("event-1", new EventId(" event-1 ").toString());
    }

}
