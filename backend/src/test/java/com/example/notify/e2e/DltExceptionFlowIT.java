package com.example.notify.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.notify.domain.exception.UserOperationExceptionRecord;
import com.example.notify.infrastructure.persistence.JdbcUserOperationExceptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * E2E-013: DLT exception flow integration test using Testcontainers
 * (PostgreSQL + Kafka + Redis).
 *
 * Tests the failure path:
 * 1. Produce a message to the DLT topic directly (simulating exhausted retries)
 * 2. DLT consumer picks it up and persists to user_operation_exception_records
 * 3. Verify exception record exists in PostgreSQL
 */
@Tag("docker")
class DltExceptionFlowIT extends AbstractIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JdbcUserOperationExceptions userOperationExceptions;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void dltFlow_userOperationEvent_messagePersistedToExceptionTable() throws Exception {
        // Produce a message to the user-operation-events-dlt topic directly.
        // This simulates what happens after retries are exhausted.
        String eventId = "dlt-event-001";
        String payload = objectMapper.writeValueAsString(Map.of(
                "eventId", eventId,
                "customerId", "customer-dlt",
                "userId", "user-dlt",
                "eventType", "PRODUCT_VIEW",
                "fields", Map.of("productId", "P001"),
                "occurredAt", Instant.now().toString()
        ));

        kafkaTemplate.send("user-operation-events-dlt", "customer-dlt", payload).get(10, TimeUnit.SECONDS);

        // DLT consumer should pick up the message and persist an exception record.
        await().atMost(30, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            List<UserOperationExceptionRecord> exceptions = userOperationExceptions.list();
            assertThat(exceptions).hasSizeGreaterThanOrEqualTo(1);
            UserOperationExceptionRecord record = exceptions.stream()
                    .filter(r -> r.eventId().value().equals(eventId))
                    .findFirst()
                    .orElse(null);
            assertThat(record).isNotNull();
            assertThat(record.customerId().value()).isEqualTo("customer-dlt");
            assertThat(record.eventType().value()).isEqualTo("PRODUCT_VIEW");
            assertThat(record.status()).isEqualTo("RETRY_EXHAUSTED");
            assertThat(record.retryCount()).isGreaterThanOrEqualTo(1);
            assertThat(record.failureReason()).isEqualTo("exhausted retries");
        });
    }

    @Test
    void dltFlow_notificationEvent_messagePersistedToExceptionTable() throws Exception {
        // Produce a message to the notification-events-dlt topic directly.
        String notificationId = "dlt-notification-001";
        LinkedHashMap<String, Object> notificationMap = new LinkedHashMap<>();
        notificationMap.put("notificationId", notificationId);
        notificationMap.put("strategyId", "strategy-dlt");
        notificationMap.put("customerId", "customer-dlt");
        notificationMap.put("userId", "user-dlt");
        notificationMap.put("eventId", "event-dlt-001");
        notificationMap.put("eventType", "PRODUCT_VIEW");
        notificationMap.put("triggeredAt", Instant.now().toString());
        notificationMap.put("window", "PT30S");
        notificationMap.put("threshold", 2);
        notificationMap.put("currentCount", 2);
        notificationMap.put("dedupeKey", "strategy-dlt:event-dlt-001");
        String payload = objectMapper.writeValueAsString(notificationMap);

        kafkaTemplate.send("notification-events-dlt", "customer-dlt", payload).get(10, TimeUnit.SECONDS);

        // Notification DLT consumer should persist exception record.
        await().atMost(30, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT notification_id, strategy_id, customer_id, status FROM notification_exception_records WHERE notification_id = ?",
                    notificationId);
            assertThat(rows).hasSize(1);
            Map<String, Object> row = rows.getFirst();
            assertThat(row.get("notification_id")).isEqualTo(notificationId);
            assertThat(row.get("strategy_id")).isEqualTo("strategy-dlt");
            assertThat(row.get("customer_id")).isEqualTo("customer-dlt");
            assertThat(row.get("status")).isEqualTo("RETRY_EXHAUSTED");
        });
    }
}
