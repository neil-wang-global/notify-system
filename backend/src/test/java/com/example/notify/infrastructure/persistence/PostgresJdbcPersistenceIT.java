package com.example.notify.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.notify.domain.event.CustomerId;
import com.example.notify.domain.event.EventId;
import com.example.notify.domain.event.EventType;
import com.example.notify.domain.event.UserId;
import com.example.notify.domain.exception.NotificationExceptionRecord;
import com.example.notify.domain.exception.UserOperationExceptionRecord;
import com.example.notify.domain.notification.NotificationEvent;
import com.example.notify.domain.notification.NotificationId;
import com.example.notify.domain.notification.NotificationRecord;
import com.example.notify.domain.strategy.IdempotencyKey;
import com.example.notify.domain.strategy.RuleAst;
import com.example.notify.domain.strategy.RuleOperator;
import com.example.notify.domain.strategy.Strategy;
import com.example.notify.domain.strategy.StrategyExecutionPlan;
import com.example.notify.domain.strategy.StrategyId;
import com.example.notify.domain.strategy.StrategyName;
import com.example.notify.domain.strategy.StrategyScope;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@EnabledIfEnvironmentVariable(named = "RUN_DOCKER_TESTS", matches = "true")
class PostgresJdbcPersistenceIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("notify")
        .withUsername("notify")
        .withPassword("notify");

    @Test
    void jdbcStrategiesUsePostgresCompatibleUpsert() {
        JdbcTemplate jdbc = jdbc();
        new PersistenceSchema(jdbc).create();
        JdbcStrategies strategies = new JdbcStrategies(jdbc);

        Strategy strategy = Strategy.create(
            new StrategyId("strategy-postgres-1"),
            new StrategyName("Postgres Strategy"),
            StrategyScope.global(),
            new RuleAst.Comparison("eventType", RuleOperator.EQ, "PRODUCT_VIEW"),
            new StrategyExecutionPlan(Duration.ofSeconds(30), Duration.ofSeconds(10), Duration.ZERO, List.of("customerId", "userId", "eventType"))
        );

        strategies.save(strategy, new IdempotencyKey("idem-postgres-1"), "fingerprint-1");
        strategies.save(strategy.update(strategy.name(), strategy.scope(), strategy.ruleAst(), strategy.executionPlan()), new IdempotencyKey("idem-postgres-2"), "fingerprint-2");

        assertTrue(strategies.find(new StrategyId("strategy-postgres-1")).isPresent());
        assertEquals(2, strategies.find(new StrategyId("strategy-postgres-1")).orElseThrow().version().value());
        assertEquals("fingerprint-2", strategies.fingerprint(new IdempotencyKey("idem-postgres-2")).orElseThrow());
    }

    @Test
    void jdbcStrategiesKeepIdempotencyInsertOnceAndRejectStaleVersion() {
        JdbcTemplate jdbc = jdbc();
        new PersistenceSchema(jdbc).create();
        JdbcStrategies strategies = new JdbcStrategies(jdbc);
        Strategy strategy = strategy("strategy-postgres-lock");
        Strategy updated = strategy.update(strategy.name(), strategy.scope(), strategy.ruleAst(), strategy.executionPlan());

        strategies.save(strategy, new IdempotencyKey("idem-postgres-once"), "fingerprint-1");
        strategies.save(strategy("strategy-postgres-other"), new IdempotencyKey("idem-postgres-once"), "fingerprint-2");
        strategies.save(updated, new IdempotencyKey("idem-postgres-lock-2"), "fingerprint-3");

        assertEquals("fingerprint-1", strategies.fingerprint(new IdempotencyKey("idem-postgres-once")).orElseThrow());
        assertEquals("strategy-postgres-lock", strategies.findByIdempotencyKey(new IdempotencyKey("idem-postgres-once")).orElseThrow().id().toString());
        assertThrows(IllegalArgumentException.class, () -> strategies.save(strategy, new IdempotencyKey("idem-postgres-lock-3"), "fingerprint-4"));
        assertEquals(2, strategies.find(new StrategyId("strategy-postgres-lock")).orElseThrow().version().value());
    }

    @Test
    void jdbcStrategiesPersistRuleRowsToPostgres() {
        JdbcTemplate jdbc = jdbc();
        new PersistenceSchema(jdbc).create();
        JdbcStrategies strategies = new JdbcStrategies(jdbc);
        Strategy strategy = Strategy.create(
            new StrategyId("strategy-postgres-rules"),
            new StrategyName("Postgres Rules"),
            StrategyScope.global(),
            new RuleAst.Group(com.example.notify.domain.strategy.RuleConnector.AND, List.of(
                new RuleAst.Comparison("eventType", RuleOperator.EQ, "PRODUCT_VIEW"),
                new RuleAst.Comparison("productId", RuleOperator.IN, List.of("P001", "P002"))
            )),
            new StrategyExecutionPlan(Duration.ofSeconds(30), Duration.ofSeconds(10), Duration.ZERO, List.of("customerId", "userId", "eventType"))
        );

        strategies.save(strategy, new IdempotencyKey("idem-postgres-rules"), "fingerprint-rules");

        assertEquals(2, jdbc.queryForObject("select count(*) from strategy_rule_items where strategy_id = ?", Integer.class, "strategy-postgres-rules"));
        assertTrue(strategies.find(new StrategyId("strategy-postgres-rules")).orElseThrow().ruleAst() instanceof RuleAst.Group);
    }

    @Test
    void jdbcExceptionRecordsRoundTripThroughPostgres() {
        JdbcTemplate jdbc = jdbc();
        new PersistenceSchema(jdbc).create();
        JdbcUserOperationExceptions userOperationExceptions = new JdbcUserOperationExceptions(jdbc);
        JdbcNotificationExceptions notificationExceptions = new JdbcNotificationExceptions(jdbc);

        userOperationExceptions.add(new UserOperationExceptionRecord(
            "user-postgres-ex-1", new EventId("event-postgres-ex-1"), new CustomerId("customer-1"),
            new EventType("PRODUCT_VIEW"), "{}", "redis unavailable", 3, "FAILED",
            Instant.parse("2026-06-08T00:00:00Z"), Instant.parse("2026-06-08T00:01:00Z")
        ));
        notificationExceptions.add(new NotificationExceptionRecord(
            "notification-postgres-ex-1", new NotificationId("notification-postgres-ex-1"), new StrategyId("strategy-postgres-ex-1"),
            new CustomerId("customer-1"), new EventId("event-postgres-ex-1"), "{}", "publish failed", 2, "FAILED",
            Instant.parse("2026-06-08T00:00:00Z"), Instant.parse("2026-06-08T00:01:00Z")
        ));

        assertTrue(userOperationExceptions.find("user-postgres-ex-1").isPresent());
        assertTrue(notificationExceptions.find("notification-postgres-ex-1").isPresent());
    }

    @Test
    void jdbcNotificationRecordsPersistInstantToPostgresTimestamp() {
        JdbcTemplate jdbc = jdbc();
        new PersistenceSchema(jdbc).create();
        JdbcNotificationRecords records = new JdbcNotificationRecords(jdbc);
        NotificationRecord record = NotificationRecord.from(new NotificationEvent(
            new NotificationId("notification-postgres-1"),
            new StrategyId("strategy-postgres-1"),
            new CustomerId("customer-1"),
            new UserId("user-1"),
            new EventId("event-1"),
            new EventType("PRODUCT_VIEW"),
            Instant.parse("2026-06-08T00:00:00Z"),
            "PT30S",
            1,
            1,
            "dedupe-postgres-1"
        ));

        assertTrue(records.addIfAbsent(record));

        assertEquals(1, records.list().size());
        assertEquals(Instant.parse("2026-06-08T00:00:00Z"), records.list().getFirst().event().triggeredAt());
    }

    private static Strategy strategy(String id) {
        return Strategy.create(
            new StrategyId(id),
            new StrategyName("Postgres Strategy"),
            StrategyScope.global(),
            new RuleAst.Comparison("eventType", RuleOperator.EQ, "PRODUCT_VIEW"),
            new StrategyExecutionPlan(Duration.ofSeconds(30), Duration.ofSeconds(10), Duration.ZERO, List.of("customerId", "userId", "eventType"))
        );
    }

    private static JdbcTemplate jdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

}
