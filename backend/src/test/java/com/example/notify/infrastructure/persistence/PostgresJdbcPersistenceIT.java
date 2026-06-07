package com.example.notify.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.notify.domain.strategy.IdempotencyKey;
import com.example.notify.domain.strategy.RuleAst;
import com.example.notify.domain.strategy.RuleOperator;
import com.example.notify.domain.strategy.Strategy;
import com.example.notify.domain.strategy.StrategyExecutionPlan;
import com.example.notify.domain.strategy.StrategyId;
import com.example.notify.domain.strategy.StrategyName;
import com.example.notify.domain.strategy.StrategyScope;
import java.time.Duration;
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
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
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

}
