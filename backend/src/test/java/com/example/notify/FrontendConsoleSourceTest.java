package com.example.notify;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FrontendConsoleSourceTest {

    private static final Path FRONTEND = Path.of("../frontend/src").toAbsolutePath().normalize();

    @Test
    void consolePagesExposeBusinessCapabilities() throws IOException {
        assertContains("pages/StrategiesPage.vue", "策略");
        assertContains("pages/RuleEditorPage.vue", "productId");
        assertContains("pages/EventSimulatorPage.vue", "PRODUCT_VIEW");
        assertContains("pages/NotificationsPage.vue", "notification");
        assertContains("pages/ExceptionsPage.vue", "Exception");
        assertContains("pages/SystemMonitorPage.vue", "Kafka");
    }

    private static void assertContains(String relativePath, String expected) throws IOException {
        assertTrue(Files.readString(FRONTEND.resolve(relativePath)).contains(expected));
    }

}
