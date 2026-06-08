package com.example.notify.interfaces.rest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class StrategiesApiTest {

    @Test
    void exposesPluralApiClassForSaveAndUpdate() throws Exception {
        Class<?> api = assertDoesNotThrow(() -> Class.forName("com.example.notify.interfaces.rest.StrategiesApi"));

        assertTrue(api.getSimpleName().endsWith("Api"));
        Method save = api.getDeclaredMethod("save", StrategiesApi.SaveStrategyRequest.class);
        Method update = api.getDeclaredMethod("update", String.class, StrategiesApi.SaveStrategyRequest.class);

        assertEquals(StrategiesApi.StrategySaveResponse.class, save.getReturnType());
        assertEquals(StrategiesApi.StrategySaveResponse.class, update.getReturnType());
    }

}
