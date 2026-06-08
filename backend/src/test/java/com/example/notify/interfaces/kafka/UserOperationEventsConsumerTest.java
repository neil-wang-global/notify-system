package com.example.notify.interfaces.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.notify.domain.notification.NotificationEvent;
import com.example.notify.domain.notification.NotificationEvents;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@Tag("docker")
@SpringBootTest
@ActiveProfiles("kafka-test")
@EmbeddedKafka(
    partitions = 1,
    topics = {"user-operation-events", "notification-events", "user-operation-events-dlt", "notification-events-dlt"}
)
@TestPropertySource(properties = {
    "notify.kafka.enabled=true",
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "spring.kafka.consumer.enable-auto-commit=false",
    "spring.kafka.consumer.group-id=notify-system-test",
    "spring.kafka.consumer.auto-offset-reset=earliest",
    "spring.kafka.producer.properties.enable.idempotence=true",
    "notify.kafka.topics.user-operation-events=user-operation-events",
    "notify.kafka.topics.notification-events=notification-events",
    "notify.kafka.topics.user-operation-events-dlt=user-operation-events-dlt",
    "notify.kafka.topics.notification-events-dlt=notification-events-dlt",
    "notify.deduplication.default-window=10s",
    "notify.window.default-size=30s",
    "notify.window.default-shard-size=10s"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UserOperationEventsConsumerTest {

    @Autowired
    EmbeddedKafkaBroker embeddedKafka;

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    TestConfig testConfig;

    @Autowired
    org.springframework.kafka.config.KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @TestConfiguration
    static class TestConfig {
        private final List<NotificationEvent> publishedNotifications = new CopyOnWriteArrayList<>();

        @Bean
        @Primary
        NotificationEvents capturingNotificationEvents() {
            return event -> publishedNotifications.add(event);
        }

        List<NotificationEvent> getPublishedNotifications() {
            return publishedNotifications;
        }
    }

    private KafkaTemplate<String, String> testProducer;

    @BeforeEach
    void setUp() {
        for (org.springframework.kafka.listener.MessageListenerContainer container :
                kafkaListenerEndpointRegistry.getListenerContainers()) {
            ContainerTestUtils.waitForAssignment(container, embeddedKafka.getPartitionsPerTopic());
        }

        Map<String, Object> producerProps = Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafka.getBrokersAsString(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class
        );
        testProducer = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));
    }

    @Test
    void consumeEventPublishesNotification() throws Exception {
        String eventJson = objectMapper.writeValueAsString(Map.of(
            "eventId", "evt-consume-test-1",
            "customerId", "cust-1",
            "userId", "user-1",
            "eventType", "PRODUCT_VIEW",
            "userGroupIds", List.of(),
            "fields", Map.of(),
            "occurredAt", Instant.now().toString()
        ));

        testProducer.send("user-operation-events", "cust-1", eventJson).get();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(testConfig.getPublishedNotifications()).isNotEmpty();
        });

        NotificationEvent notification = testConfig.getPublishedNotifications().get(0);
        assertThat(notification.customerId().value()).isEqualTo("cust-1");
        assertThat(notification.userId().value()).isEqualTo("user-1");
        assertThat(notification.eventId().value()).isEqualTo("evt-consume-test-1");
    }

    @Test
    void duplicateEventIdIsHandledIdempotently() throws Exception {
        String eventJson = objectMapper.writeValueAsString(Map.of(
            "eventId", "evt-dup-test-1",
            "customerId", "cust-dup",
            "userId", "user-dup",
            "eventType", "PRODUCT_VIEW",
            "userGroupIds", List.of(),
            "fields", Map.of(),
            "occurredAt", Instant.now().toString()
        ));

        testProducer.send("user-operation-events", "cust-dup", eventJson).get();
        testProducer.send("user-operation-events", "cust-dup", eventJson).get();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<NotificationEvent> dupNotifications = testConfig.getPublishedNotifications().stream()
                .filter(e -> "evt-dup-test-1".equals(e.eventId().value()))
                .toList();
            assertThat(dupNotifications).hasSizeGreaterThanOrEqualTo(1);
        });

        long dupCount = testConfig.getPublishedNotifications().stream()
            .filter(e -> "evt-dup-test-1".equals(e.eventId().value()))
            .map(e -> e.eventId().value())
            .distinct()
            .count();

        assertThat(dupCount).isEqualTo(1);
    }

}
