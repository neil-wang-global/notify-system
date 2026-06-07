package com.example.notify.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.notify.application.strategy.SaveStrategy;
import com.example.notify.domain.event.User;
import com.example.notify.domain.event.UserId;
import com.example.notify.domain.event.Users;
import com.example.notify.domain.strategy.IdempotencyKey;
import com.example.notify.domain.strategy.RuleAst;
import com.example.notify.domain.strategy.RuleOperator;
import com.example.notify.domain.strategy.StrategyExecutionPlan;
import com.example.notify.domain.strategy.StrategyId;
import com.example.notify.domain.strategy.StrategyName;
import com.example.notify.domain.strategy.StrategyScope;
import com.example.notify.domain.strategy.StrategyVersion;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcSaveStrategyTest {

    private SaveStrategy saveStrategy;
    private JdbcStrategies strategies;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:jdbc-save-strategy-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        new PersistenceSchema(jdbc).create();
        strategies = new JdbcStrategies(jdbc);
        Users users = token -> new User(new UserId(token.toString()), List.of());
        saveStrategy = new SaveStrategy(strategies, users);
    }

    @Test
    void saveStrategyPersistsThroughJdbcAndReusesSameIdempotencyKey() {
        SaveStrategy.Command command = command("strategy-jdbc-save", "idem-jdbc-save", Optional.empty());

        SaveStrategy.Result first = saveStrategy.save(command);
        SaveStrategy.Result second = saveStrategy.save(command);

        assertEquals(1, first.strategy().version().value());
        assertEquals(1, second.strategy().version().value());
        assertEquals("strategy-jdbc-save", strategies.find(new StrategyId("strategy-jdbc-save")).orElseThrow().id().toString());
        assertEquals("strategy-jdbc-save", strategies.findByIdempotencyKey(new IdempotencyKey("idem-jdbc-save")).orElseThrow().id().toString());
    }

    @Test
    void updateStrategyPersistsNextVersionThroughJdbc() {
        saveStrategy.save(command("strategy-jdbc-update", "idem-jdbc-update-1", Optional.empty()));

        SaveStrategy.Result updated = saveStrategy.save(command("strategy-jdbc-update", "idem-jdbc-update-2", Optional.of(new StrategyVersion(1))));

        assertEquals(2, updated.strategy().version().value());
        assertEquals(2, strategies.find(new StrategyId("strategy-jdbc-update")).orElseThrow().version().value());
    }

    @Test
    void staleExpectedVersionIsRejectedAgainstJdbcState() {
        saveStrategy.save(command("strategy-jdbc-stale", "idem-jdbc-stale-1", Optional.empty()));
        saveStrategy.save(command("strategy-jdbc-stale", "idem-jdbc-stale-2", Optional.of(new StrategyVersion(1))));

        assertThrows(IllegalArgumentException.class, () -> saveStrategy.save(command("strategy-jdbc-stale", "idem-jdbc-stale-3", Optional.of(new StrategyVersion(1)))));
    }

    private static SaveStrategy.Command command(String strategyId, String idempotencyKey, Optional<StrategyVersion> expectedVersion) {
        return new SaveStrategy.Command(
            new StrategyId(strategyId),
            new StrategyName("JDBC Strategy"),
            StrategyScope.global(),
            new RuleAst.Comparison("eventType", RuleOperator.EQ, "PRODUCT_VIEW"),
            new StrategyExecutionPlan(Duration.ofSeconds(30), Duration.ofSeconds(10), Duration.ZERO, List.of("customerId", "userId", "eventType")),
            expectedVersion,
            new com.example.notify.domain.event.UserToken("token-1"),
            new IdempotencyKey(idempotencyKey),
            Instant.parse("2026-06-08T00:00:00Z")
        );
    }

}
