package com.example.notify.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.notify.domain.event.CustomerId;
import com.example.notify.domain.event.EventId;
import com.example.notify.domain.event.EventType;
import com.example.notify.domain.event.UserGroupId;
import com.example.notify.domain.event.UserId;
import com.example.notify.domain.exception.NotificationExceptionRecord;
import com.example.notify.domain.exception.UserOperationExceptionRecord;
import com.example.notify.domain.notification.NotificationEvent;
import com.example.notify.domain.notification.NotificationId;
import com.example.notify.domain.notification.NotificationRecord;
import com.example.notify.config.DataSourceRole;
import com.example.notify.config.DataSourceRoleContext;
import com.example.notify.domain.strategy.IdempotencyKey;
import com.example.notify.domain.strategy.RuleAst;
import com.example.notify.domain.strategy.RuleOperator;
import com.example.notify.domain.strategy.Strategy;
import com.example.notify.domain.strategy.StrategyExecutionPlan;
import com.example.notify.domain.strategy.StrategyId;
import com.example.notify.domain.strategy.StrategyName;
import com.example.notify.domain.strategy.StrategyScope;
import com.example.notify.engine.matching.EventSnapshot;
import com.example.notify.engine.matching.RuleAstEvaluator;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
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
    void jdbcStrategyQueriesRunInReadDatasourceContext() {
        AtomicReference<DataSourceRole> queryRole = new AtomicReference<>();
        JdbcTemplate observingJdbc = new JdbcTemplate(jdbc.getDataSource()) {
            @Override
            public <T> List<T> query(String sql, org.springframework.jdbc.core.RowMapper<T> rowMapper, Object... args) {
                queryRole.set(DataSourceRoleContext.current());
                return super.query(sql, rowMapper, args);
            }
        };
        JdbcStrategies strategies = new JdbcStrategies(observingJdbc);
        strategies.save(strategy("strategy-db-read", "PRODUCT_VIEW"), new IdempotencyKey("idem-db-read"), "fingerprint-read");

        strategies.find(new StrategyId("strategy-db-read"));

        assertEquals(DataSourceRole.READ, queryRole.get());
    }

    @Test
    void persistenceSchemaAddsRuleAstJsonToExistingStrategiesTable() {
        DataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:jdbc-migration-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate migrationJdbc = new JdbcTemplate(dataSource);
        migrationJdbc.execute("""
            create table strategies (
                id varchar(128) primary key,
                name varchar(256) not null,
                scope_kind varchar(64) not null,
                rule_field varchar(128) not null,
                rule_operator varchar(64) not null,
                rule_value varchar(512) not null,
                window_size_seconds bigint not null,
                shard_size_seconds bigint not null,
                business_dedup_seconds bigint not null,
                version integer not null
            )
            """);

        new PersistenceSchema(migrationJdbc).create();

        Integer columns = migrationJdbc.queryForObject("""
            select count(*)
            from information_schema.columns
            where table_name = 'STRATEGIES' and column_name = 'RULE_AST_JSON'
            """, Integer.class);
        assertEquals(1, columns);
    }

    @Test
    void jdbcStrategiesRollBackStrategyWhenRuleRowsFail() {
        jdbc.execute("alter table strategy_rule_items add constraint reject_event_type_rule check (field <> 'eventType')");
        JdbcStrategies strategies = new JdbcStrategies(jdbc);

        assertThrows(RuntimeException.class, () -> strategies.save(
            strategy("strategy-db-rollback", "PRODUCT_VIEW"),
            new IdempotencyKey("idem-db-rollback"),
            "fingerprint-rollback"
        ));

        assertTrue(strategies.find(new StrategyId("strategy-db-rollback")).isEmpty());
        assertTrue(strategies.fingerprint(new IdempotencyKey("idem-db-rollback")).isEmpty());
    }

    @Test
    void jdbcStrategiesRejectStaleVersionOverwrite() {
        JdbcStrategies strategies = new JdbcStrategies(jdbc);
        Strategy created = strategy("strategy-db-lock", "PRODUCT_VIEW");
        Strategy updated = created.update(created.name(), created.scope(), created.ruleAst(), created.executionPlan());

        strategies.save(created, new IdempotencyKey("idem-db-lock-1"), "fingerprint-lock-1");
        strategies.save(updated, new IdempotencyKey("idem-db-lock-2"), "fingerprint-lock-2");

        assertThrows(IllegalArgumentException.class, () -> strategies.save(created, new IdempotencyKey("idem-db-lock-3"), "fingerprint-lock-3"));
        assertEquals(2, strategies.find(new StrategyId("strategy-db-lock")).orElseThrow().version().value());
    }

    @Test
    void jdbcStrategiesDoNotOverwriteExistingIdempotencyFingerprint() {
        JdbcStrategies strategies = new JdbcStrategies(jdbc);
        IdempotencyKey idempotencyKey = new IdempotencyKey("idem-db-once");

        strategies.save(strategy("strategy-db-once-1", "PRODUCT_VIEW"), idempotencyKey, "fingerprint-1");
        strategies.save(strategy("strategy-db-once-2", "PRODUCT_VIEW"), idempotencyKey, "fingerprint-2");

        assertEquals("fingerprint-1", strategies.fingerprint(idempotencyKey).orElseThrow());
        assertEquals("strategy-db-once-1", strategies.findByIdempotencyKey(idempotencyKey).orElseThrow().id().toString());
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
        Integer rows = jdbc.queryForObject("select count(*) from strategy_rule_items where strategy_id = ?", Integer.class, "strategy-db-group");
        assertEquals(2, rows);
    }

    @Test
    void jdbcStrategiesPersistScopeIds() {
        JdbcStrategies strategies = new JdbcStrategies(jdbc);
        Strategy usersStrategy = Strategy.create(
            new StrategyId("strategy-db-users-scope"),
            new StrategyName("Users Scope Strategy"),
            StrategyScope.users(new UserId("user-1"), new UserId("user-2")),
            new RuleAst.Comparison("eventType", RuleOperator.EQ, "PRODUCT_VIEW"),
            new StrategyExecutionPlan(Duration.ofSeconds(30), Duration.ofSeconds(10), Duration.ZERO, List.of("customerId", "userId", "eventType"))
        );
        Strategy groupsStrategy = Strategy.create(
            new StrategyId("strategy-db-groups-scope"),
            new StrategyName("Groups Scope Strategy"),
            StrategyScope.userGroups(new UserGroupId("group-1")),
            new RuleAst.Comparison("eventType", RuleOperator.EQ, "PRODUCT_VIEW"),
            new StrategyExecutionPlan(Duration.ofSeconds(30), Duration.ofSeconds(10), Duration.ZERO, List.of("customerId", "userId", "eventType"))
        );

        strategies.save(usersStrategy, new IdempotencyKey("idem-db-users-scope"), "fingerprint-users-scope");
        strategies.save(groupsStrategy, new IdempotencyKey("idem-db-groups-scope"), "fingerprint-groups-scope");

        StrategyScope usersScope = strategies.find(new StrategyId("strategy-db-users-scope")).orElseThrow().scope();
        StrategyScope groupsScope = strategies.find(new StrategyId("strategy-db-groups-scope")).orElseThrow().scope();
        assertEquals(List.of(new UserId("user-1"), new UserId("user-2")), usersScope.userIds());
        assertEquals(List.of(new UserGroupId("group-1")), groupsScope.userGroupIds());
    }

    @Test
    void jdbcStrategiesPreserveStructuredRuleValuesAfterRoundTrip() {
        JdbcStrategies strategies = new JdbcStrategies(jdbc);
        RuleAst ast = new RuleAst.Group(com.example.notify.domain.strategy.RuleConnector.AND, List.of(
            new RuleAst.Comparison("eventType", RuleOperator.EQ, "PRODUCT_VIEW"),
            new RuleAst.Comparison("productId", RuleOperator.IN, List.of("P001", "P002"))
        ));
        Strategy strategy = Strategy.create(
            new StrategyId("strategy-db-structured"),
            new StrategyName("Structured Rule Strategy"),
            StrategyScope.global(),
            ast,
            new StrategyExecutionPlan(Duration.ofSeconds(30), Duration.ofSeconds(10), Duration.ZERO, List.of("customerId", "userId", "eventType"))
        );

        strategies.save(strategy, new IdempotencyKey("idem-db-structured"), "fingerprint-structured");

        RuleAst restored = strategies.find(new StrategyId("strategy-db-structured")).orElseThrow().ruleAst();
        EventSnapshot snapshot = new EventSnapshot("customer-1", "user-1", Set.of(), "PRODUCT_VIEW", Map.of("productId", "P001"));
        assertTrue(new RuleAstEvaluator().matches(restored, snapshot));
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
