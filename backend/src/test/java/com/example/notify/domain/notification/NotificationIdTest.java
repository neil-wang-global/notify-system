package com.example.notify.domain.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class NotificationIdTest {

    @Test
    void sameValueIsEqual() {
        assertEquals(new NotificationId("notification-1"), new NotificationId("notification-1"));
    }

    @Test
    void differentValueIsNotEqual() {
        assertNotEquals(new NotificationId("notification-1"), new NotificationId("notification-2"));
    }

    @Test
    void rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> new NotificationId(null));
    }

    @Test
    void rejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new NotificationId("  "));
    }

    @Test
    void trimsAndConvertsToString() {
        assertEquals("notification-1", new NotificationId(" notification-1 ").toString());
    }

}
