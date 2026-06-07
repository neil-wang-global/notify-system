package com.example.notify.infrastructure.persistence;

import com.example.notify.domain.strategy.IdempotencyKey;
import com.example.notify.domain.strategy.RuleAst;
import com.example.notify.domain.strategy.RuleOperator;
import com.example.notify.domain.strategy.Strategies;
import com.example.notify.domain.strategy.Strategy;
import com.example.notify.domain.strategy.StrategyExecutionPlan;
import com.example.notify.domain.strategy.StrategyId;
import com.example.notify.domain.strategy.StrategyName;
import com.example.notify.domain.strategy.StrategyScope;
import com.example.notify.domain.strategy.StrategyVersion;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcStrategies implements Strategies {

    private final JdbcTemplate jdbc;
    private final boolean h2;

    public JdbcStrategies(JdbcTemplate jdbc) {
        if (jdbc == null) {
            throw new IllegalArgumentException("jdbcTemplate must not be null");
        }
        this.jdbc = jdbc;
        this.h2 = isH2(jdbc);
    }

    @Override
    public Optional<Strategy> find(StrategyId strategyId) {
        List<Strategy> rows = jdbc.query("""
                select id, name, scope_kind, rule_field, rule_operator, rule_value,
                       window_size_seconds, shard_size_seconds, business_dedup_seconds, version
                from strategies where id = ?
                """,
            (rs, rowNum) -> new Strategy(
                new StrategyId(rs.getString("id")),
                new StrategyName(rs.getString("name")),
                new StrategyScope(StrategyScope.Kind.valueOf(rs.getString("scope_kind")), List.of(), List.of()),
                new RuleAst.Comparison(rs.getString("rule_field"), RuleOperator.valueOf(rs.getString("rule_operator")), rs.getString("rule_value")),
                new StrategyExecutionPlan(
                    Duration.ofSeconds(rs.getLong("window_size_seconds")),
                    Duration.ofSeconds(rs.getLong("shard_size_seconds")),
                    Duration.ofSeconds(rs.getLong("business_dedup_seconds")),
                    List.of("customerId", "userId", "eventType")
                ),
                new StrategyVersion(rs.getInt("version"))
            ),
            strategyId.toString()
        );
        return rows.stream().findFirst();
    }

    @Override
    public Optional<Strategy> findByIdempotencyKey(IdempotencyKey idempotencyKey) {
        List<String> ids = jdbc.query("select strategy_id from strategy_idempotency_keys where idempotency_key = ?", (rs, rowNum) -> rs.getString("strategy_id"), idempotencyKey.toString());
        return ids.stream().findFirst().flatMap(id -> find(new StrategyId(id)));
    }

    @Override
    public Optional<String> fingerprint(IdempotencyKey idempotencyKey) {
        return jdbc.query("select fingerprint from strategy_idempotency_keys where idempotency_key = ?", (rs, rowNum) -> rs.getString("fingerprint"), idempotencyKey.toString())
            .stream()
            .findFirst();
    }

    @Override
    public void save(Strategy strategy, IdempotencyKey idempotencyKey, String fingerprint) {
        RuleAst.Comparison comparison = (RuleAst.Comparison) strategy.ruleAst();
        jdbc.update(strategyUpsertSql(),
            strategy.id().toString(),
            strategy.name().value(),
            strategy.scope().kind().name(),
            comparison.field(),
            comparison.operator().name(),
            String.valueOf(comparison.value()),
            strategy.executionPlan().windowSize().toSeconds(),
            strategy.executionPlan().shardSize().toSeconds(),
            strategy.executionPlan().businessDedupWindow().toSeconds(),
            strategy.version().value()
        );
        jdbc.update(idempotencyUpsertSql(),
            idempotencyKey.toString(), strategy.id().toString(), fingerprint);
    }

    private String strategyUpsertSql() {
        if (h2) {
            return "merge into strategies key(id) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        }
        return """
            insert into strategies (
                id, name, scope_kind, rule_field, rule_operator, rule_value,
                window_size_seconds, shard_size_seconds, business_dedup_seconds, version
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (id) do update set
                name = excluded.name,
                scope_kind = excluded.scope_kind,
                rule_field = excluded.rule_field,
                rule_operator = excluded.rule_operator,
                rule_value = excluded.rule_value,
                window_size_seconds = excluded.window_size_seconds,
                shard_size_seconds = excluded.shard_size_seconds,
                business_dedup_seconds = excluded.business_dedup_seconds,
                version = excluded.version
            """;
    }

    private String idempotencyUpsertSql() {
        if (h2) {
            return "merge into strategy_idempotency_keys key(idempotency_key) values (?, ?, ?)";
        }
        return """
            insert into strategy_idempotency_keys (idempotency_key, strategy_id, fingerprint)
            values (?, ?, ?)
            on conflict (idempotency_key) do update set
                strategy_id = excluded.strategy_id,
                fingerprint = excluded.fingerprint
            """;
    }

    private static boolean isH2(JdbcTemplate jdbc) {
        try {
            String database = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>) connection -> connection.getMetaData().getDatabaseProductName());
            return database != null && database.toLowerCase().contains("h2");
        } catch (Exception ignored) {
            return false;
        }
    }

}
