package com.example.notify.domain.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CustomerIdTest {

    @Test
    void sameValueIsEqual() {
        assertEquals(new CustomerId("customer-1"), new CustomerId("customer-1"));
    }

    @Test
    void differentValueIsNotEqual() {
        assertNotEquals(new CustomerId("customer-1"), new CustomerId("customer-2"));
    }

    @Test
    void rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> new CustomerId(null));
    }

    @Test
    void rejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new CustomerId("  "));
    }

    @Test
    void trimsAndConvertsToString() {
        assertEquals("customer-1", new CustomerId(" customer-1 ").toString());
    }

}
