package com.example.notify.interfaces.rest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class RuntimeApisTest {

    @Test
    void exposesEventNotificationExceptionAndStatusApis() throws Exception {
        Class<?> events = assertDoesNotThrow(() -> Class.forName("com.example.notify.interfaces.rest.EventsApi"));
        Class<?> notifications = assertDoesNotThrow(() -> Class.forName("com.example.notify.interfaces.rest.NotificationsApi"));
        Class<?> exceptions = assertDoesNotThrow(() -> Class.forName("com.example.notify.interfaces.rest.ExceptionsApi"));
        Class<?> status = assertDoesNotThrow(() -> Class.forName("com.example.notify.interfaces.rest.StatusApi"));

        assertTrue(events.getSimpleName().endsWith("Api"));
        assertTrue(notifications.getSimpleName().endsWith("Api"));
        assertTrue(exceptions.getSimpleName().endsWith("Api"));
        assertTrue(status.getSimpleName().endsWith("Api"));

        Method simulate = events.getDeclaredMethod("simulate", EventsApi.UserOperationEventRequest.class);
        Method listNotifications = notifications.getDeclaredMethod("list");
        Method listUserOperationExceptions = exceptions.getDeclaredMethod("userOperationExceptions");
        Method listNotificationExceptions = exceptions.getDeclaredMethod("notificationExceptions");
        Method current = status.getDeclaredMethod("current");

        assertEquals(EventsApi.EventResponse.class, simulate.getReturnType());
        assertEquals(java.util.List.class, listNotifications.getReturnType());
        assertEquals(java.util.List.class, listUserOperationExceptions.getReturnType());
        assertEquals(java.util.List.class, listNotificationExceptions.getReturnType());
        assertEquals(StatusApi.StatusResponse.class, current.getReturnType());
    }

}
