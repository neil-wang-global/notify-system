package com.example.notify.infrastructure.persistence;

import com.example.notify.config.DataSourceRoleContext;
import com.example.notify.domain.event.UserGroupId;
import com.example.notify.domain.event.UserId;
import com.example.notify.domain.strategy.IdempotencyKey;
import com.example.notify.domain.strategy.RuleAst;
import com.example.notify.domain.strategy.RuleConnector;
import com.example.notify.domain.strategy.RuleOperator;
import com.example.notify.domain.strategy.Strategies;
import com.example.notify.domain.strategy.Strategy;
import com.example.notify.domain.strategy.StrategyExecutionPlan;
import com.example.notify.domain.strategy.StrategyId;
import com.example.notify.domain.strategy.StrategyName;
import com.example.notify.domain.strategy.StrategyScope;
import com.example.notify.domain.strategy.StrategyVersion;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class JdbcStrategies implements Strategies {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final boolean h2;

    public JdbcStrategies(JdbcTemplate jdbc) {
        if (jdbc == null) {
            throw new IllegalArgumentException("jdbcTemplate must not be null");
        }
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        this.h2 = isH2(jdbc);
    }

    @Override
    public Optional<Strategy> find(StrategyId strategyId) {
        return DataSourceRoleContext.read(() -> {
            List<Strategy> rows = jdbc.query("""
                    select id, name, scope_kind, rule_field, rule_operator, rule_value, rule_ast_json,
                           window_size_seconds, shard_size_seconds, business_dedup_seconds, version
                    from strategies where id = ?
                    """,
                (rs, rowNum) -> {
                    StrategyScope scope = restoreScope(strategyId.toString(), StrategyScope.Kind.valueOf(rs.getString("scope_kind")));
                    return new Strategy(
                        new StrategyId(rs.getString("id")),
                        new StrategyName(rs.getString("name")),
                        scope,
                        astFrom(rs.getString("rule_ast_json"), rs.getString("rule_field"), rs.getString("rule_operator"), rs.getString("rule_value")),
                        new StrategyExecutionPlan(
                            Duration.ofSeconds(rs.getLong("window_size_seconds")),
                            Duration.ofSeconds(rs.getLong("shard_size_seconds")),
                            Duration.ofSeconds(rs.getLong("business_dedup_seconds")),
                            List.of("customerId", "userId", "eventType")
                        ),
                        new StrategyVersion(rs.getInt("version"))
                    );
                },
                strategyId.toString()
            );
            return rows.stream().findFirst();
        });
    }

    private StrategyScope restoreScope(String strategyId, StrategyScope.Kind kind) {
        if (kind == StrategyScope.Kind.GLOBAL) {
            return StrategyScope.global();
        }
        List<String> scopeIds = jdbc.query(
            "select scope_id from strategy_scope_ids where strategy_id = ? and id_kind = ?",
            (rs, rowNum) -> rs.getString("scope_id"),
            strategyId, kind.name()
        );
        return switch (kind) {
            case USERS -> StrategyScope.users(scopeIds.stream().map(UserId::new).toArray(UserId[]::new));
            case USER_GROUPS -> StrategyScope.userGroups(scopeIds.stream().map(UserGroupId::new).toArray(UserGroupId[]::new));
            case GLOBAL -> StrategyScope.global();
        };
    }

    @Override
    public Optional<Strategy> findByIdempotencyKey(IdempotencyKey idempotencyKey) {
        List<String> ids = DataSourceRoleContext.read(() -> jdbc.query("select strategy_id from strategy_idempotency_keys where idempotency_key = ?", (rs, rowNum) -> rs.getString("strategy_id"), idempotencyKey.toString()));
        return ids.stream().findFirst().flatMap(id -> find(new StrategyId(id)));
    }

    @Override
    public Optional<String> fingerprint(IdempotencyKey idempotencyKey) {
        return DataSourceRoleContext.read(() -> jdbc.query("select fingerprint from strategy_idempotency_keys where idempotency_key = ?", (rs, rowNum) -> rs.getString("fingerprint"), idempotencyKey.toString())
            .stream()
            .findFirst());
    }

    @Override
    public void save(Strategy strategy, IdempotencyKey idempotencyKey, String fingerprint) {
        transaction.executeWithoutResult(status -> saveInTransaction(strategy, idempotencyKey, fingerprint));
    }

    private void saveInTransaction(Strategy strategy, IdempotencyKey idempotencyKey, String fingerprint) {
        rejectStaleVersion(strategy);
        RuleAst.Comparison comparison = firstComparison(strategy.ruleAst());
        int changed = jdbc.update(strategyUpsertSql(),
            strategy.id().toString(),
            strategy.name().value(),
            strategy.scope().kind().name(),
            comparison.field(),
            comparison.operator().name(),
            String.valueOf(comparison.value()),
            astJson(strategy.ruleAst()),
            strategy.executionPlan().windowSize().toSeconds(),
            strategy.executionPlan().shardSize().toSeconds(),
            strategy.executionPlan().businessDedupWindow().toSeconds(),
            strategy.version().value()
        );
        if (changed == 0) {
            throw new IllegalArgumentException("strategy version conflict");
        }
        jdbc.update("delete from strategy_rule_items where strategy_id = ?", strategy.id().toString());
        persistRuleRows(strategy.id(), strategy.ruleAst());
        persistScopeIds(strategy);
        try {
            jdbc.update(idempotencyInsertSql(), idempotencyKey.toString(), strategy.id().toString(), fingerprint);
        } catch (DuplicateKeyException ignored) {
            // Idempotency keys are insert-once. Application logic validates duplicate fingerprints before saving.
        }
    }

    private void rejectStaleVersion(Strategy strategy) {
        find(strategy.id()).ifPresent(existing -> {
            if (existing.version().value() >= strategy.version().value()) {
                throw new IllegalArgumentException("strategy version conflict");
            }
        });
    }

    private void persistRuleRows(StrategyId strategyId, RuleAst ast) {
        List<RuleRow> rows = ruleRows(ast, "default", RuleConnector.AND, new java.util.concurrent.atomic.AtomicInteger(1));
        for (RuleRow row : rows) {
            jdbc.update("""
                    insert into strategy_rule_items (
                        strategy_id, sort_order, group_id, connector, field, operator, value_type, value_json
                    ) values (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                strategyId.toString(), row.sortOrder(), row.groupId(), row.connector().name(), row.comparison().field(),
                row.comparison().operator().name(), valueType(row.comparison().value()), valueJson(row.comparison().value()));
        }
    }

    private void persistScopeIds(Strategy strategy) {
        jdbc.update("delete from strategy_scope_ids where strategy_id = ?", strategy.id().toString());
        StrategyScope scope = strategy.scope();
        String kind = scope.kind().name();
        switch (scope.kind()) {
            case GLOBAL -> { /* no scope ids */ }
            case USERS -> {
                for (UserId userId : scope.userIds()) {
                    jdbc.update("insert into strategy_scope_ids (strategy_id, id_kind, scope_id) values (?, ?, ?)",
                        strategy.id().toString(), kind, userId.toString());
                }
            }
            case USER_GROUPS -> {
                for (UserGroupId groupId : scope.userGroupIds()) {
                    jdbc.update("insert into strategy_scope_ids (strategy_id, id_kind, scope_id) values (?, ?, ?)",
                        strategy.id().toString(), kind, groupId.toString());
                }
            }
        }
    }

    private String strategyUpsertSql() {
        if (h2) {
            return """
                merge into strategies (
                    id, name, scope_kind, rule_field, rule_operator, rule_value, rule_ast_json,
                    window_size_seconds, shard_size_seconds, business_dedup_seconds, version
                ) key(id) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        }
        return """
            insert into strategies (
                id, name, scope_kind, rule_field, rule_operator, rule_value, rule_ast_json,
                window_size_seconds, shard_size_seconds, business_dedup_seconds, version
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (id) do update set
                name = excluded.name,
                scope_kind = excluded.scope_kind,
                rule_field = excluded.rule_field,
                rule_operator = excluded.rule_operator,
                rule_value = excluded.rule_value,
                rule_ast_json = excluded.rule_ast_json,
                window_size_seconds = excluded.window_size_seconds,
                shard_size_seconds = excluded.shard_size_seconds,
                business_dedup_seconds = excluded.business_dedup_seconds,
                version = excluded.version
            where strategies.version = excluded.version - 1
            """;
    }

    private static RuleAst.Comparison firstComparison(RuleAst ast) {
        return switch (ast) {
            case RuleAst.Comparison comparison -> comparison;
            case RuleAst.Group group -> firstComparison(group.children().getFirst());
            case RuleAst.Not not -> firstComparison(not.child());
        };
    }

    private static List<RuleRow> ruleRows(RuleAst ast, String groupId, RuleConnector connector, java.util.concurrent.atomic.AtomicInteger sortOrder) {
        return switch (ast) {
            case RuleAst.Comparison comparison -> List.of(new RuleRow(groupId, connector, comparison, sortOrder.getAndIncrement()));
            case RuleAst.Not not -> ruleRows(not.child(), groupId, connector, sortOrder);
            case RuleAst.Group group -> group.children().stream()
                .flatMap(child -> ruleRows(child, groupId, group.connector(), sortOrder).stream())
                .toList();
        };
    }

    private static String valueType(Object value) {
        if (value instanceof List<?> values && values.stream().allMatch(Number.class::isInstance)) {
            return "NUMBER_LIST";
        }
        if (value instanceof List<?>) {
            return "STRING_LIST";
        }
        if (value instanceof Number) {
            return "NUMBER";
        }
        if (value instanceof Boolean) {
            return "BOOLEAN";
        }
        return "STRING";
    }

    private static String valueJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("rule value could not be serialized", exception);
        }
    }

    private record RuleRow(String groupId, RuleConnector connector, RuleAst.Comparison comparison, int sortOrder) {
    }

    private static String astJson(RuleAst ast) {
        try {
            return JSON.writeValueAsString(toJson(ast));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("rule AST could not be serialized", exception);
        }
    }

    private static RuleAst astFrom(String json, String field, String operator, String value) {
        if (json == null || json.isBlank()) {
            return new RuleAst.Comparison(field, RuleOperator.valueOf(operator), value);
        }
        try {
            return fromJson(JSON.readValue(json, new TypeReference<Map<String, Object>>() { }));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("rule AST could not be deserialized", exception);
        }
    }

    private static Map<String, Object> toJson(RuleAst ast) {
        return switch (ast) {
            case RuleAst.Comparison comparison -> Map.of(
                "type", "comparison",
                "field", comparison.field(),
                "operator", comparison.operator().name(),
                "value", comparison.value()
            );
            case RuleAst.Group group -> Map.of(
                "type", "group",
                "connector", group.connector().name(),
                "children", group.children().stream().map(JdbcStrategies::toJson).toList()
            );
            case RuleAst.Not not -> Map.of(
                "type", "not",
                "child", toJson(not.child())
            );
        };
    }

    @SuppressWarnings("unchecked")
    private static RuleAst fromJson(Map<String, Object> json) {
        return switch (String.valueOf(json.get("type"))) {
            case "comparison" -> new RuleAst.Comparison(
                String.valueOf(json.get("field")),
                RuleOperator.valueOf(String.valueOf(json.get("operator"))),
                json.get("value")
            );
            case "group" -> new RuleAst.Group(
                RuleConnector.valueOf(String.valueOf(json.get("connector"))),
                ((List<Map<String, Object>>) json.get("children")).stream().map(JdbcStrategies::fromJson).toList()
            );
            case "not" -> new RuleAst.Not(fromJson((Map<String, Object>) json.get("child")));
            default -> throw new IllegalArgumentException("unknown rule AST type: " + json.get("type"));
        };
    }

    private String idempotencyInsertSql() {
        if (h2) {
            return "insert into strategy_idempotency_keys (idempotency_key, strategy_id, fingerprint) values (?, ?, ?)";
        }
        return """
            insert into strategy_idempotency_keys (idempotency_key, strategy_id, fingerprint)
            values (?, ?, ?)
            on conflict (idempotency_key) do nothing
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
