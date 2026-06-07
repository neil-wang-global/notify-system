package com.example.notify.domain.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class UserIdTest {

    @Test
    void sameValueIsEqual() {
        assertEquals(new UserId("user-1"), new UserId("user-1"));
    }

    @Test
    void differentValueIsNotEqual() {
        assertNotEquals(new UserId("user-1"), new UserId("user-2"));
    }

    @Test
    void rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> new UserId(null));
    }

    @Test
    void rejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new UserId("  "));
    }

    @Test
    void trimsAndConvertsToString() {
        assertEquals("user-1", new UserId(" user-1 ").toString());
    }

}
