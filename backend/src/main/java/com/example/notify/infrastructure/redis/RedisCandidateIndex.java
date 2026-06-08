package com.example.notify.infrastructure.redis;

import com.example.notify.domain.event.EventType;
import com.example.notify.domain.event.UserGroupId;
import com.example.notify.domain.event.UserId;
import com.example.notify.domain.strategy.StrategyId;
import com.example.notify.domain.strategy.StrategyScope;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis-backed candidate index using Redis sets for scope, event type, and field lookups.
 * <p>
 * Key layout:
 * <ul>
 *   <li>{@code idx:scope:global} — set of global strategy IDs</li>
 *   <li>{@code idx:scope:user:{userId}} — set of strategy IDs for a user</li>
 *   <li>{@code idx:scope:group:{groupId}} — set of strategy IDs for a group</li>
 *   <li>{@code idx:eventType:{eventType}} — set of strategy IDs for an event type</li>
 *   <li>{@code idx:field:{field}:{value}} — set of strategy IDs for a field value</li>
 * </ul>
 */
public final class RedisCandidateIndex {

    private static final String IDX_SCOPE_GLOBAL = "idx:scope:global";
    private static final String IDX_SCOPE_USER = "idx:scope:user:";
    private static final String IDX_SCOPE_GROUP = "idx:scope:group:";
    private static final String IDX_EVENT_TYPE = "idx:eventType:";
    private static final String IDX_FIELD = "idx:field:";
    /** Default TTL for index entries (24 hours). */
    private static final Duration INDEX_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;

    public RedisCandidateIndex(StringRedisTemplate redis) {
        if (redis == null) {
            throw new IllegalArgumentException("StringRedisTemplate must not be null");
        }
        this.redis = redis;
    }

    /**
     * Add a strategy to the scope, event type, and field indexes using a pipeline.
     * Index entries are given a configurable TTL to prevent stale data accumulation.
     */
    public void index(StrategyId strategyId, StrategyScope scope, EventType eventType, List<RedisFieldIndex> fieldIndexes) {
        if (strategyId == null || scope == null || eventType == null || fieldIndexes == null) {
            throw new IllegalArgumentException("index state is incomplete");
        }
        String id = strategyId.value();
        redis.executePipelined((RedisCallback<Object>) connection -> {
            byte[] rawId = id.getBytes(StandardCharsets.UTF_8);

            // Scope index entries with TTL
            if (scope.kind() == StrategyScope.Kind.GLOBAL) {
                addSetMemberWithTtl(connection, IDX_SCOPE_GLOBAL.getBytes(StandardCharsets.UTF_8), rawId);
            }
            for (UserId uid : scope.userIds()) {
                addSetMemberWithTtl(connection, (IDX_SCOPE_USER + uid.value()).getBytes(StandardCharsets.UTF_8), rawId);
            }
            for (UserGroupId gid : scope.userGroupIds()) {
                addSetMemberWithTtl(connection, (IDX_SCOPE_GROUP + gid.value()).getBytes(StandardCharsets.UTF_8), rawId);
            }

            // Event type index with TTL
            addSetMemberWithTtl(connection, (IDX_EVENT_TYPE + eventType.value()).getBytes(StandardCharsets.UTF_8), rawId);

            // Field indexes with TTL
            for (RedisFieldIndex fi : fieldIndexes) {
                addSetMemberWithTtl(connection, (IDX_FIELD + fi.field() + ":" + fi.value()).getBytes(StandardCharsets.UTF_8), rawId);
            }

            return null;
        });
    }

    /**
     * Remove a strategy from the scope, event type, and field indexes using a pipeline.
     */
    public void remove(StrategyId strategyId, StrategyScope scope, EventType eventType, List<RedisFieldIndex> fieldIndexes) {
        if (strategyId == null || scope == null || eventType == null || fieldIndexes == null) {
            throw new IllegalArgumentException("remove state is incomplete");
        }
        String id = strategyId.value();
        redis.executePipelined((RedisCallback<Object>) connection -> {
            byte[] rawId = id.getBytes(StandardCharsets.UTF_8);

            if (scope.kind() == StrategyScope.Kind.GLOBAL) {
                connection.setCommands().sRem(IDX_SCOPE_GLOBAL.getBytes(StandardCharsets.UTF_8), rawId);
            }
            for (UserId uid : scope.userIds()) {
                connection.setCommands().sRem((IDX_SCOPE_USER + uid.value()).getBytes(StandardCharsets.UTF_8), rawId);
            }
            for (UserGroupId gid : scope.userGroupIds()) {
                connection.setCommands().sRem((IDX_SCOPE_GROUP + gid.value()).getBytes(StandardCharsets.UTF_8), rawId);
            }
            connection.setCommands().sRem((IDX_EVENT_TYPE + eventType.value()).getBytes(StandardCharsets.UTF_8), rawId);
            for (RedisFieldIndex fi : fieldIndexes) {
                connection.setCommands().sRem((IDX_FIELD + fi.field() + ":" + fi.value()).getBytes(StandardCharsets.UTF_8), rawId);
            }

            return null;
        });
    }

    /**
     * Atomically remove old indexes and add new indexes in a single pipeline round-trip (T-15).
     */
    public void reindex(StrategyId strategyId, StrategyScope oldScope, EventType oldEventType, List<RedisFieldIndex> oldFieldIndexes,
                        StrategyScope newScope, EventType newEventType, List<RedisFieldIndex> newFieldIndexes) {
        if (strategyId == null) {
            throw new IllegalArgumentException("strategyId must not be null");
        }
        String id = strategyId.value();
        redis.executePipelined((RedisCallback<Object>) connection -> {
            byte[] rawId = id.getBytes(StandardCharsets.UTF_8);

            // Remove old indexes
            if (oldScope != null && oldEventType != null && oldFieldIndexes != null) {
                if (oldScope.kind() == StrategyScope.Kind.GLOBAL) {
                    connection.setCommands().sRem(IDX_SCOPE_GLOBAL.getBytes(StandardCharsets.UTF_8), rawId);
                }
                for (UserId uid : oldScope.userIds()) {
                    connection.setCommands().sRem((IDX_SCOPE_USER + uid.value()).getBytes(StandardCharsets.UTF_8), rawId);
                }
                for (UserGroupId gid : oldScope.userGroupIds()) {
                    connection.setCommands().sRem((IDX_SCOPE_GROUP + gid.value()).getBytes(StandardCharsets.UTF_8), rawId);
                }
                connection.setCommands().sRem((IDX_EVENT_TYPE + oldEventType.value()).getBytes(StandardCharsets.UTF_8), rawId);
                for (RedisFieldIndex fi : oldFieldIndexes) {
                    connection.setCommands().sRem((IDX_FIELD + fi.field() + ":" + fi.value()).getBytes(StandardCharsets.UTF_8), rawId);
                }
            }

            // Add new indexes with TTL
            if (newScope != null && newEventType != null && newFieldIndexes != null) {
                if (newScope.kind() == StrategyScope.Kind.GLOBAL) {
                    addSetMemberWithTtl(connection, IDX_SCOPE_GLOBAL.getBytes(StandardCharsets.UTF_8), rawId);
                }
                for (UserId uid : newScope.userIds()) {
                    addSetMemberWithTtl(connection, (IDX_SCOPE_USER + uid.value()).getBytes(StandardCharsets.UTF_8), rawId);
                }
                for (UserGroupId gid : newScope.userGroupIds()) {
                    addSetMemberWithTtl(connection, (IDX_SCOPE_GROUP + gid.value()).getBytes(StandardCharsets.UTF_8), rawId);
                }
                addSetMemberWithTtl(connection, (IDX_EVENT_TYPE + newEventType.value()).getBytes(StandardCharsets.UTF_8), rawId);
                for (RedisFieldIndex fi : newFieldIndexes) {
                    addSetMemberWithTtl(connection, (IDX_FIELD + fi.field() + ":" + fi.value()).getBytes(StandardCharsets.UTF_8), rawId);
                }
            }

            return null;
        });
    }

    /**
     * Compute candidate strategy IDs for a given event context.
     * <p>
     * Algorithm: union of scope candidates, intersect with event type, intersect with each field index.
     * Only field indexes that actually have entries are used for intersection (T-24).
     *
     * @param customerId   customer ID (reserved)
     * @param userId       user ID for scope lookup
     * @param userGroupIds user group IDs for scope lookup
     * @param eventType    event type for filtering
     * @param fields       event fields for field index filtering
     * @return matching strategy IDs
     */
    public Set<StrategyId> candidates(String customerId, String userId, Set<String> userGroupIds,
                                       String eventType, java.util.Map<String, String> fields) {
        if (userId == null || userId.isBlank() || eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("candidate lookup identity is incomplete");
        }
        if (userGroupIds == null || fields == null) {
            throw new IllegalArgumentException("candidate lookup collections must not be null");
        }

        // Step 1: scope candidates (union of global + user + groups)
        Set<String> scopeCandidates = new HashSet<>();
        Set<String> global = redis.opsForSet().members(IDX_SCOPE_GLOBAL);
        if (global != null) {
            scopeCandidates.addAll(global);
        }
        Set<String> user = redis.opsForSet().members(IDX_SCOPE_USER + userId);
        if (user != null) {
            scopeCandidates.addAll(user);
        }
        for (String groupId : userGroupIds) {
            Set<String> group = redis.opsForSet().members(IDX_SCOPE_GROUP + groupId);
            if (group != null) {
                scopeCandidates.addAll(group);
            }
        }

        // Step 2: intersect with event type
        Set<String> eventCandidates = redis.opsForSet().members(IDX_EVENT_TYPE + eventType);
        if (eventCandidates != null) {
            scopeCandidates.retainAll(eventCandidates);
        }

        // Step 3: intersect with each field index — T-24: only intersect if the field index has entries
        for (var entry : fields.entrySet()) {
            Set<String> fieldCandidates = redis.opsForSet().members(IDX_FIELD + entry.getKey() + ":" + entry.getValue());
            if (fieldCandidates != null && !fieldCandidates.isEmpty()) {
                scopeCandidates.retainAll(fieldCandidates);
            }
        }

        return scopeCandidates.stream()
                .map(StrategyId::new)
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Add a member to a Redis set and set/refresh the TTL on the key.
     */
    private void addSetMemberWithTtl(org.springframework.data.redis.connection.RedisConnection connection, byte[] key, byte[] member) {
        connection.setCommands().sAdd(key, member);
        connection.keyCommands().expire(key, INDEX_TTL);
    }
}
