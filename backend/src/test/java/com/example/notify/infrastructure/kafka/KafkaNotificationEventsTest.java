package com.example.notify.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.notify.config.NotifyProperties;
import com.example.notify.domain.event.CustomerId;
import com.example.notify.domain.event.EventId;
import com.example.notify.domain.event.EventType;
import com.example.notify.domain.event.UserId;
import com.example.notify.domain.notification.NotificationEvent;
import com.example.notify.domain.notification.NotificationId;
import com.example.notify.domain.notification.NotificationRecords;
import com.example.notify.domain.strategy.StrategyId;import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

@Tag("docker")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KafkaNotificationEventsTest {

    private static final String NOTIFICATION_EVENTS = "notification-events";

    private EmbeddedKafkaKraftBroker embeddedKafka;
    private KafkaTemplate<String, String> kafkaTemplate;
    private ObjectMapper objectMapper;

    @BeforeAll
    void startBroker() {
        embeddedKafka = new EmbeddedKafkaKraftBroker(1, 1, NOTIFICATION_EVENTS);
        embeddedKafka.afterPropertiesSet();

        Map<String, Object> producerProps = Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafka.getBrokersAsString(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class
        );
        kafkaTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));

        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
    }

    @AfterAll
    void stopBroker() {
        if (embeddedKafka != null) {
            embeddedKafka.destroy();
        }
    }

    @Test
    void kafkaTemplateSendAndReceive() throws Exception {
        String key = "template-key";
        String value = objectMapper.writeValueAsString(Map.of("test", "template"));

        var future = kafkaTemplate.send(NOTIFICATION_EVENTS, key, value);
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                System.err.println("Send failed: " + ex.getMessage());
            }
        });
        kafkaTemplate.flush();

        try (KafkaConsumer<String, String> consumer = createConsumer()) {
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(2));
                assertThat(records.count()).isGreaterThan(0);
                for (var record : records) {
                    assertThat(record.key()).isEqualTo(key);
                }
            });
        }
    }

    @Test
    void publishNotificationEventAppearsInTopic() throws Exception {
        NotifyProperties props = testProps();

        KafkaNotificationEvents publisher = new KafkaNotificationEvents(
            kafkaTemplate, props, objectMapper);

        NotificationEvent event = new NotificationEvent(
            new NotificationId("notif-test-1"),
            new StrategyId("strategy-1"),
            new CustomerId("cust-1"),
            new UserId("user-1"),
            new EventId("evt-1"),
            new EventType("PRODUCT_VIEW"),
            Instant.now(),
            "PT30S",
            1,
            1,
            "strategy-1:evt-1"
        );

        publisher.publish(event);
        kafkaTemplate.flush();

        try (KafkaConsumer<String, String> consumer = createConsumer()) {
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(2));
                assertThat(records.count()).isGreaterThan(0);
                for (var record : records) {
                    assertThat(record.key()).isEqualTo("cust-1");
                }
            });
        }
    }

    @Test
    void usesCustomerIdAsKey() throws Exception {
        NotifyProperties props = testProps();

        KafkaNotificationEvents publisher = new KafkaNotificationEvents(
            kafkaTemplate, props, objectMapper);

        NotificationEvent event = new NotificationEvent(
            new NotificationId("notif-key-test"),
            new StrategyId("strategy-key"),
            new CustomerId("cust-key-abc"),
            new UserId("user-key"),
            new EventId("evt-key"),
            new EventType("PRODUCT_VIEW"),
            Instant.now(),
            "PT30S",
            1,
            1,
            "strategy-key:evt-key"
        );

        publisher.publish(event);
        kafkaTemplate.flush();

        try (KafkaConsumer<String, String> consumer = createConsumer()) {
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(2));
                assertThat(records.count()).isGreaterThan(0);
                boolean found = false;
                for (var record : records) {
                    if ("cust-key-abc".equals(record.key())) {
                        found = true;
                    }
                }
                assertThat(found).isTrue();
            });
        }
    }

    private KafkaConsumer<String, String> createConsumer() {
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafka.getBrokersAsString());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "notification-consumer-test-" + System.nanoTime());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps);
        consumer.subscribe(List.of(NOTIFICATION_EVENTS));
        return consumer;
    }

    private NotifyProperties testProps() {
        return new NotifyProperties(
            new NotifyProperties.Kafka(new NotifyProperties.Topics(
                "user-operation-events", "notification-events", "uop-dlt", "notif-dlt")),
            new NotifyProperties.Deduplication(true, Duration.ofSeconds(10), List.of()),
            new NotifyProperties.Window(Duration.ofSeconds(30), Duration.ofSeconds(10), Map.of())
        );
    }

}
