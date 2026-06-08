package com.example.notify.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.notify.application.event.ProcessUserOperationEvent;
import com.example.notify.domain.event.CustomerId;
import com.example.notify.domain.event.EventId;
import com.example.notify.domain.event.EventType;
import com.example.notify.domain.event.UserId;
import com.example.notify.domain.notification.NotificationRecord;
import com.example.notify.domain.strategy.RuleAst;
import com.example.notify.domain.strategy.StrategyExecutionPlan;
import com.example.notify.domain.strategy.StrategyId;
import com.example.notify.engine.matching.EventSnapshot;
import com.example.notify.engine.timebox.TimeboxOperations;
import com.example.notify.infrastructure.persistence.JdbcNotificationRecords;
import com.example.notify.infrastructure.persistence.JdbcNotificationRecords;
import com.example.notify.infrastructure.persistence.PersistenceSchema;
import com.example.notify.infrastructure.redis.RealRedisStrategies;
import com.example.notify.domain.strategy.Strategy;
import com.example.notify.domain.strategy.StrategyName;
import com.example.notify.domain.strategy.StrategyScope;
import com.example.notify.domain.strategy.StrategyVersion;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * E2E-013: Full notification flow integration test using Testcontainers
 * (PostgreSQL + Kafka + Redis).
 *
 * Tests the complete happy path:
 * 1. Save strategy to PostgreSQL via JDBC
 * 2. Refresh Redis strategy cache (RealRedisStrategies)
 * 3. Process two user-operation events through ProcessUserOperationEvent
 * 4. First event: count = 1 (below threshold)
 * 5. Second event: threshold reached -> notification published to notification-events Kafka topic
 * 6. NotificationEventsConsumer picks it up and persists to notification_records
 * 7. Verify notification record exists in PostgreSQL via JDBC query
 */
@Tag("docker")
class FullNotificationFlowIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private PersistenceSchema persistenceSchema;

    @Autowired
    private JdbcNotificationRecords notificationRecords;

    @Autowired
    private ProcessUserOperationEvent processUserOperationEvent;

    @Autowired
    private TimeboxOperations timeboxOperations;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    private static final StrategyId STRATEGY_ID = new StrategyId("e2e-strategy-1");
    private static final CustomerId CUSTOMER_ID = new CustomerId("customer-e2e");
    private static final UserId USER_ID = new UserId("user-e2e");
    private static final EventType EVENT_TYPE = new EventType("PRODUCT_VIEW");

    @BeforeEach
    void cleanUp() {
        // Flush Redis to ensure clean state
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void fullChain_saveStrategyToPg_processEvents_thresholdReached_notificationPersisted() {
        Instant now = Instant.now();

        // Step 1: Strategy is already persisted by JdbcStrategies (schema created by PersistenceSchema bean).
        // We save the strategy directly via JDBC to simulate what SaveStrategy does.
        saveStrategyViaJdbc();

        // Step 2: Refresh Redis strategy cache so the strategy is discoverable.
        RealRedisStrategies realRedisStrategies = new RealRedisStrategies(redisTemplate, new com.example.notify.config.DegradationState());
        Strategy domainStrategy = new Strategy(STRATEGY_ID,
                new com.example.notify.domain.strategy.StrategyName("test"),
                StrategyScope.global(),
                new RuleAst.Comparison("eventType", com.example.notify.domain.strategy.RuleOperator.EQ, "PRODUCT_VIEW"),
                new StrategyExecutionPlan(Duration.ofSeconds(30), Duration.ofSeconds(10), Duration.ZERO,
                        List.of("customerId", "userId", "eventType")),
                2,
                new StrategyVersion(1));
        boolean accepted = realRedisStrategies.refresh(domainStrategy);
        assertThat(accepted).isTrue();

        // Step 3: Build matched strategy for the processing pipeline.
        ProcessUserOperationEvent.MatchedStrategy matchedStrategy = new ProcessUserOperationEvent.MatchedStrategy(
                STRATEGY_ID,
                new RuleAst.Comparison("eventType", com.example.notify.domain.strategy.RuleOperator.EQ, "PRODUCT_VIEW"),
                new StrategyExecutionPlan(Duration.ofSeconds(30), Duration.ofSeconds(10), Duration.ZERO,
                        List.of("customerId", "userId", "eventType")),
                2  // threshold = 2 events
        );

        // Step 4: Process first event -> count = 1, not triggered.
        EventSnapshot snapshot = new EventSnapshot(
                CUSTOMER_ID.value(), USER_ID.value(), Set.of(), EVENT_TYPE.value(), Map.of("productId", "P001"));

        processUserOperationEvent.process(
                new EventId("event-e2e-001"), snapshot, List.of(matchedStrategy), now);

        // Verify no notification record yet (threshold not reached)
        List<NotificationRecord> recordsBefore = notificationRecords.list();
        assertThat(recordsBefore).isEmpty();

        // Step 5: Process second event -> count = 2, threshold reached -> notification published.
        processUserOperationEvent.process(
                new EventId("event-e2e-002"), snapshot, List.of(matchedStrategy), now.plusMillis(100));

        // Step 6: Verify notification record exists in PostgreSQL.
        // When Kafka is enabled, KafkaNotificationEvents.publish() first persists to notification_records
        // then publishes to Kafka. So the record should be in PG immediately.
        await().atMost(10, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
            List<NotificationRecord> records = notificationRecords.list();
            assertThat(records).hasSize(1);
            NotificationRecord record = records.getFirst();
            assertThat(record.event().strategyId()).isEqualTo(STRATEGY_ID);
            assertThat(record.event().customerId()).isEqualTo(CUSTOMER_ID);
            assertThat(record.event().userId()).isEqualTo(USER_ID);
            assertThat(record.event().eventType()).isEqualTo(EVENT_TYPE);
            assertThat(record.event().threshold()).isEqualTo(2);
            assertThat(record.event().currentCount()).isEqualTo(2);
        });
    }

    @Test
    void fullChain_saveStrategy_queryFromPg_verifyPersistence() {
        // Verify that JDBC strategy persistence works end-to-end with real PostgreSQL.
        saveStrategyViaJdbc();

        // Query back the strategy
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, name, scope_kind, rule_field, rule_operator, rule_value, version FROM strategies WHERE id = ?",
                STRATEGY_ID.toString());

        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.getFirst();
        assertThat(row.get("id")).isEqualTo(STRATEGY_ID.toString());
        assertThat(row.get("name")).isEqualTo("E2E Test Strategy");
        assertThat(row.get("scope_kind")).isEqualTo("GLOBAL");
        assertThat(row.get("rule_field")).isEqualTo("eventType");
        assertThat(row.get("rule_operator")).isEqualTo("EQ");
        assertThat(row.get("rule_value")).isEqualTo("PRODUCT_VIEW");
        assertThat(row.get("version")).isEqualTo(1);
    }

    @Test
    void fullChain_redisTimeboxCounter_thresholdReached_withRealRedis() {
        // Verify Redis-based timebox counter works end-to-end.
        // Uses RedisTimeboxCounter wired by Spring because StringRedisTemplate is available.

        EventSnapshot snapshot = new EventSnapshot(
                CUSTOMER_ID.value(), USER_ID.value(), Set.of(), EVENT_TYPE.value(), Map.of("productId", "P001"));

        ProcessUserOperationEvent.MatchedStrategy matchedStrategy = new ProcessUserOperationEvent.MatchedStrategy(
                new StrategyId("e2e-timebox-strategy"),
                new RuleAst.Comparison("eventType", com.example.notify.domain.strategy.RuleOperator.EQ, "PRODUCT_VIEW"),
                new StrategyExecutionPlan(Duration.ofSeconds(30), Duration.ofSeconds(10), Duration.ZERO,
                        List.of("customerId", "userId", "eventType")),
                3  // threshold = 3
        );

        Instant baseTime = Instant.parse("2026-06-08T12:00:00Z");

        // First event: count = 1, not triggered
        processUserOperationEvent.process(
                new EventId("tb-event-001"), snapshot, List.of(matchedStrategy), baseTime);
        assertThat(notificationRecords.list()).isEmpty();

        // Second event: count = 2, not triggered
        processUserOperationEvent.process(
                new EventId("tb-event-002"), snapshot, List.of(matchedStrategy), baseTime.plusSeconds(1));
        assertThat(notificationRecords.list()).isEmpty();

        // Third event: count = 3, threshold reached -> notification published
        processUserOperationEvent.process(
                new EventId("tb-event-003"), snapshot, List.of(matchedStrategy), baseTime.plusSeconds(2));

        await().atMost(10, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
            List<NotificationRecord> records = notificationRecords.list();
            assertThat(records).hasSize(1);
            assertThat(records.getFirst().event().currentCount()).isEqualTo(3);
        });
    }

    private void saveStrategyViaJdbc() {
        // Insert a strategy directly via JDBC, simulating what JdbcStrategies.save() does.
        // Using merge-into syntax for H2-compatible upsert (but we're on real PG here).
        jdbcTemplate.update("""
                INSERT INTO strategies (
                    id, name, scope_kind, rule_field, rule_operator, rule_value, rule_ast_json,
                    window_size_seconds, shard_size_seconds, business_dedup_seconds, threshold, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    name = excluded.name,
                    scope_kind = excluded.scope_kind,
                    rule_field = excluded.rule_field,
                    rule_operator = excluded.rule_operator,
                    rule_value = excluded.rule_value,
                    rule_ast_json = excluded.rule_ast_json,
                    window_size_seconds = excluded.window_size_seconds,
                    shard_size_seconds = excluded.shard_size_seconds,
                    business_dedup_seconds = excluded.business_dedup_seconds,
                    threshold = excluded.threshold,
                    version = excluded.version
                """,
                STRATEGY_ID.toString(),
                "E2E Test Strategy",
                "GLOBAL",
                "eventType",
                "EQ",
                "PRODUCT_VIEW",
                "{\"type\":\"comparison\",\"field\":\"eventType\",\"operator\":\"EQ\",\"value\":\"PRODUCT_VIEW\"}",
                30L,
                10L,
                0L,
                2,
                1);
    }
}
