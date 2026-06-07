package com.example.notify.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcPersistenceTest {

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:jdbc-persistence-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        new PersistenceSchema(jdbc).create();
    }

    @Test
    void jdbcStrategiesPersistStrategyAndIdempotencyFingerprint() {
        JdbcStrategies strategies = new JdbcStrategies(jdbc);
        Strategy strategy = strategy("strategy-db-1", "PRODUCT_VIEW");
        IdempotencyKey idempotencyKey = new IdempotencyKey("idem-db-1");

        strategies.save(strategy, idempotencyKey, "fingerprint-1");

        assertTrue(strategies.find(new StrategyId("strategy-db-1")).isPresent());
        assertTrue(strategies.findByIdempotencyKey(idempotencyKey).isPresent());
        assertEquals("fingerprint-1", strategies.fingerprint(idempotencyKey).orElseThrow());
    }

    @Test
    void jdbcStrategiesPersistGroupedRuleAst() {
        JdbcStrategies strategies = new JdbcStrategies(jdbc);
        RuleAst ast = new RuleAst.Group(com.example.notify.domain.strategy.RuleConnector.AND, List.of(
            new RuleAst.Comparison("eventType", RuleOperator.EQ, "PRODUCT_VIEW"),
            new RuleAst.Comparison("productId", RuleOperator.EQ, "P001")
        ));
        Strategy strategy = Strategy.create(
            new StrategyId("strategy-db-group"),
            new StrategyName("Grouped Strategy"),
            StrategyScope.global(),
            ast,
            new StrategyExecutionPlan(Duration.ofSeconds(30), Duration.ofSeconds(10), Duration.ZERO, List.of("customerId", "userId", "eventType"))
        );

        strategies.save(strategy, new IdempotencyKey("idem-db-group"), "fingerprint-group");

        assertTrue(strategies.find(new StrategyId("strategy-db-group")).orElseThrow().ruleAst() instanceof RuleAst.Group);
    }

    @Test
    void jdbcNotificationRecordsInsertOnlyOnceByNotificationId() {
        JdbcNotificationRecords records = new JdbcNotificationRecords(jdbc);
        NotificationRecord record = NotificationRecord.from(notification("notification-db-1"));

        assertTrue(records.addIfAbsent(record));
        assertFalse(records.addIfAbsent(record));

        assertEquals(1, records.list().size());
    }

    @Test
    void jdbcExceptionRecordsAreQueryable() {
        JdbcUserOperationExceptions userOperationExceptions = new JdbcUserOperationExceptions(jdbc);
        JdbcNotificationExceptions notificationExceptions = new JdbcNotificationExceptions(jdbc);
        UserOperationExceptionRecord userRecord = new UserOperationExceptionRecord(
            "user-ex-1",
            new EventId("event-1"),
            new CustomerId("customer-1"),
            new EventType("PRODUCT_VIEW"),
            "{}",
            "redis unavailable",
            3,
            "FAILED",
            Instant.parse("2026-06-08T00:00:00Z"),
            Instant.parse("2026-06-08T00:01:00Z")
        );
        NotificationExceptionRecord notificationRecord = new NotificationExceptionRecord(
            "notification-ex-1",
            new NotificationId("notification-1"),
            new StrategyId("strategy-1"),
            new CustomerId("customer-1"),
            new EventId("event-1"),
            "{}",
            "publish failed",
            3,
            "FAILED",
            Instant.parse("2026-06-08T00:00:00Z"),
            Instant.parse("2026-06-08T00:01:00Z")
        );

        userOperationExceptions.add(userRecord);
        notificationExceptions.add(notificationRecord);

        assertTrue(userOperationExceptions.find("user-ex-1").isPresent());
        assertTrue(notificationExceptions.find("notification-ex-1").isPresent());
    }

    private static Strategy strategy(String id, String eventType) {
        return Strategy.create(
            new StrategyId(id),
            new StrategyName("DB Strategy"),
            StrategyScope.global(),
            new RuleAst.Comparison("eventType", RuleOperator.EQ, eventType),
            new StrategyExecutionPlan(Duration.ofSeconds(30), Duration.ofSeconds(10), Duration.ZERO, List.of("customerId", "userId", "eventType"))
        );
    }

    private static NotificationEvent notification(String id) {
        return new NotificationEvent(
            new NotificationId(id),
            new StrategyId("strategy-1"),
            new CustomerId("customer-1"),
            new UserId("user-1"),
            new EventId("event-1"),
            new EventType("PRODUCT_VIEW"),
            Instant.parse("2026-06-08T00:00:00Z"),
            "PT30S",
            1,
            1,
            "dedupe-1"
        );
    }

}
