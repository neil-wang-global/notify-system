package com.example.notify.infrastructure.redis;

import com.example.notify.domain.event.EventType;
import com.example.notify.domain.event.UserGroupId;
import com.example.notify.domain.event.UserId;
import com.example.notify.domain.strategy.StrategyExecutionPlan;
import com.example.notify.domain.strategy.StrategyId;
import com.example.notify.domain.strategy.StrategyScope;
import com.example.notify.domain.strategy.StrategyVersion;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * Stores execution plans and strategy metadata as Redis hashes with a version guard.
 * <p>
 * Key layout: {@code strategy:plan:{strategyId}} with hash fields:
 * <ul>
 *   <li>{@code version} — strategy version (int)</li>
 *   <li>{@code windowSize} — execution plan window size</li>
 *   <li>{@code shardSize} — execution plan shard size</li>
 *   <li>{@code businessDedupWindow} — business dedup window</li>
 *   <li>{@code dedupFields} — comma-separated dedup field names</li>
 *   <li>{@code scopeKind} — GLOBAL / USERS / USER_GROUPS</li>
 *   <li>{@code scopeUserIds} — comma-separated user IDs</li>
 *   <li>{@code scopeGroupIds} — comma-separated group IDs</li>
 *   <li>{@code eventType} — event type value</li>
 *   <li>{@code fieldIndexes} — pipe-separated field:value pairs</li>
 * </ul>
 */
public final class RedisStrategyCache {

    private static final String KEY_PREFIX = "strategy:plan:";
    private static final String VERSION_GUARD_SCRIPT = """
            local current = redis.call('HGET', KEYS[1], 'version')
            if current and tonumber(current) > tonumber(ARGV[1]) then
                return 0
            end
            redis.call('HSET', KEYS[1],
                'version', ARGV[1],
                'windowSize', ARGV[2],
                'shardSize', ARGV[3],
                'businessDedupWindow', ARGV[4],
                'dedupFields', ARGV[5],
                'scopeKind', ARGV[6],
                'scopeUserIds', ARGV[7],
                'scopeGroupIds', ARGV[8],
                'eventType', ARGV[9],
                'fieldIndexes', ARGV[10])
            return 1
            """;

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> versionGuardScript;

    public RedisStrategyCache(StringRedisTemplate redis) {
        if (redis == null) {
            throw new IllegalArgumentException("StringRedisTemplate must not be null");
        }
        this.redis = redis;
        this.versionGuardScript = new DefaultRedisScript<>(VERSION_GUARD_SCRIPT, Long.class);
    }

    StringRedisTemplate redis() {
        return redis;
    }

    /**
     * Save a full strategy with version guard.
     *
     * @return true if saved, false if rejected by stale version
     */
    public boolean save(RedisStrategy strategy) {
        if (strategy == null) {
            throw new IllegalArgumentException("redis strategy must not be null");
        }
        String key = KEY_PREFIX + strategy.strategyId().value();
        StrategyExecutionPlan plan = strategy.executionPlan();
        Long result = redis.execute(
                versionGuardScript,
                List.of(key),
                String.valueOf(strategy.version().value()),
                plan.windowSize().toSeconds() + "s",
                plan.shardSize().toSeconds() + "s",
                plan.businessDedupWindow().toSeconds() + "s",
                String.join(",", plan.dedupFields()),
                strategy.scope().kind().name(),
                strategy.scope().userIds().stream().map(UserId::value).reduce((a, b) -> a + "," + b).orElse(""),
                strategy.scope().userGroupIds().stream().map(UserGroupId::value).reduce((a, b) -> a + "," + b).orElse(""),
                strategy.eventType().value(),
                strategy.fieldIndexes().stream().map(fi -> fi.field() + ":" + fi.value()).reduce((a, b) -> a + "|" + b).orElse("")
        );
        return result != null && result == 1L;
    }

    /**
     * Load the full cached strategy metadata from Redis.
     *
     * @return the cached strategy, or empty if not found
     */
    public Optional<RedisStrategy> load(StrategyId strategyId) {
        if (strategyId == null) {
            throw new IllegalArgumentException("strategyId must not be null");
        }
        String key = KEY_PREFIX + strategyId.value();
        Map<Object, Object> entries = redis.opsForHash().entries(key);
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        StrategyVersion version = new StrategyVersion(Integer.parseInt((String) entries.get("version")));
        Duration windowSize = parseDuration((String) entries.get("windowSize"));
        Duration shardSize = parseDuration((String) entries.get("shardSize"));
        Duration businessDedupWindow = parseDuration((String) entries.get("businessDedupWindow"));
        String dedupFieldsRaw = (String) entries.get("dedupFields");
        List<String> dedupFields = dedupFieldsRaw == null || dedupFieldsRaw.isBlank()
                ? List.of()
                : List.of(dedupFieldsRaw.split(","));
        StrategyExecutionPlan plan = new StrategyExecutionPlan(windowSize, shardSize, businessDedupWindow, dedupFields);

        String scopeKindRaw = (String) entries.get("scopeKind");
        StrategyScope.Kind scopeKind = scopeKindRaw != null ? StrategyScope.Kind.valueOf(scopeKindRaw) : StrategyScope.Kind.GLOBAL;
        String scopeUserIdsRaw = (String) entries.get("scopeUserIds");
        List<UserId> userIds = parseList(scopeUserIdsRaw).stream().map(UserId::new).toList();
        String scopeGroupIdsRaw = (String) entries.get("scopeGroupIds");
        List<UserGroupId> groupIds = parseList(scopeGroupIdsRaw).stream().map(UserGroupId::new).toList();
        StrategyScope scope = switch (scopeKind) {
            case GLOBAL -> StrategyScope.global();
            case USERS -> StrategyScope.users(userIds.toArray(UserId[]::new));
            case USER_GROUPS -> StrategyScope.userGroups(groupIds.toArray(UserGroupId[]::new));
        };

        String eventTypeRaw = (String) entries.get("eventType");
        EventType eventType = eventTypeRaw != null ? new EventType(eventTypeRaw) : new EventType("UNKNOWN");

        String fieldIndexesRaw = (String) entries.get("fieldIndexes");
        List<RedisFieldIndex> fieldIndexes = parseFieldIndexes(fieldIndexesRaw);

        return Optional.of(new RedisStrategy(strategyId, version, plan, scope, eventType, fieldIndexes));
    }

    /**
     * Load only the execution plan (no index metadata).
     */
    public Optional<StrategyExecutionPlan> loadPlan(StrategyId strategyId) {
        return load(strategyId).map(RedisStrategy::executionPlan);
    }

    private static Duration parseDuration(String text) {
        if (text == null || text.isBlank()) {
            return Duration.ZERO;
        }
        if (text.endsWith("s")) {
            return Duration.ofSeconds(Long.parseLong(text.substring(0, text.length() - 1)));
        }
        return Duration.parse(text);
    }

    private static List<String> parseList(String csv) {
        if (csv == null || csv.isBlank()) {
            return Collections.emptyList();
        }
        return List.of(csv.split(","));
    }

    private static List<RedisFieldIndex> parseFieldIndexes(String pipeSeparated) {
        if (pipeSeparated == null || pipeSeparated.isBlank()) {
            return Collections.emptyList();
        }
        return List.of(pipeSeparated.split("\\|"))
                .stream()
                .map(pair -> {
                    String[] parts = pair.split(":", 2);
                    return new RedisFieldIndex(parts[0], parts[1]);
                })
                .toList();
    }
}
