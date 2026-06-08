package com.example.notify;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DeliveryArtifactsTest {

    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void frontendConsoleContainsRequiredPages() {
        assertTrue(Files.exists(ROOT.resolve("frontend/package.json")));
        assertTrue(Files.exists(ROOT.resolve("frontend/src/pages/StrategiesPage.vue")));
        assertTrue(Files.exists(ROOT.resolve("frontend/src/pages/RuleEditorPage.vue")));
        assertTrue(Files.exists(ROOT.resolve("frontend/src/pages/EventSimulatorPage.vue")));
        assertTrue(Files.exists(ROOT.resolve("frontend/src/pages/NotificationsPage.vue")));
        assertTrue(Files.exists(ROOT.resolve("frontend/src/pages/ExceptionsPage.vue")));
        assertTrue(Files.exists(ROOT.resolve("frontend/src/pages/SystemMonitorPage.vue")));
    }

    @Test
    void infrastructureAndBenchmarkArtifactsExist() {
        assertTrue(Files.exists(ROOT.resolve("docker-compose.yml")));
        assertTrue(Files.exists(ROOT.resolve("sql/schema.sql")));
        assertTrue(Files.exists(ROOT.resolve("scripts/benchmark-events.sh")));
        assertTrue(Files.exists(ROOT.resolve("reports/pressure-test-report.md")));
    }

}
