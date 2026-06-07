package com.example.notify;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class InfrastructurePlanComplianceTest {

    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void dockerComposeModelsPrimaryReplicaPostgresAndMessaging() throws IOException {
        String compose = Files.readString(ROOT.resolve("docker-compose.yml"));

        assertTrue(compose.contains("postgres-primary"));
        assertTrue(compose.contains("postgres-replica"));
        assertTrue(compose.contains("user-operation-events"));
        assertTrue(compose.contains("notification-events"));
        assertTrue(compose.contains("user-operation-events-dlt"));
        assertTrue(compose.contains("notification-events-dlt"));
        assertTrue(compose.contains("redis"));
    }

    @Test
    void backendDeclaresIntegrationDependencies() throws IOException {
        String build = Files.readString(ROOT.resolve("backend/build.gradle.kts"));

        assertTrue(build.contains("spring-boot-starter-data-jdbc"));
        assertTrue(build.contains("spring-kafka"));
        assertTrue(build.contains("spring-boot-starter-data-redis"));
        assertTrue(build.contains("testcontainers"));
        assertTrue(build.contains("postgresql"));
    }

    @Test
    void pressureReportContainsMeasuredRunNotTemplateOnly() throws IOException {
        String report = Files.readString(ROOT.resolve("reports/pressure-test-report.md"));

        assertTrue(report.contains("## Measured Run"));
        assertTrue(report.contains("messagesSent="));
        assertTrue(report.contains("durationMs="));
        assertTrue(report.contains("throughputMsgPerSecond="));
    }

}
