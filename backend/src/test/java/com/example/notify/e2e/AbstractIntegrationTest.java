package com.example.notify.e2e;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.sql.init.mode=never",
        "notify.kafka.enabled=true",
        "spring.kafka.consumer.group-id=notify-system-test",
        "notify.kafka.topics.user-operation-events=user-operation-events",
        "notify.kafka.topics.notification-events=notification-events",
        "notify.kafka.topics.user-operation-events-dlt=user-operation-events-dlt",
        "notify.kafka.topics.notification-events-dlt=notification-events-dlt",
        "notify.datasource.routing-enabled=false"
})
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("notify_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    @SuppressWarnings("resource")
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name",
                () -> "org.postgresql.Driver");

        // Kafka
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);

        // Redis
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
}
