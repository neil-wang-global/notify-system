package com.example.notify.domain.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class UserGroupIdTest {

    @Test
    void sameValueIsEqual() {
        assertEquals(new UserGroupId("group-1"), new UserGroupId("group-1"));
    }

    @Test
    void differentValueIsNotEqual() {
        assertNotEquals(new UserGroupId("group-1"), new UserGroupId("group-2"));
    }

    @Test
    void rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> new UserGroupId(null));
    }

    @Test
    void rejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new UserGroupId("  "));
    }

    @Test
    void trimsAndConvertsToString() {
        assertEquals("group-1", new UserGroupId(" group-1 ").toString());
    }

}
