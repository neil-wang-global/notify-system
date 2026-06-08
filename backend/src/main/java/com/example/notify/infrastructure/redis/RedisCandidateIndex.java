package com.example.notify.infrastructure.redis;

import com.example.notify.domain.event.EventType;
import com.example.notify.domain.event.UserGroupId;
import com.example.notify.domain.event.UserId;
import com.example.notify.domain.strategy.StrategyId;
import com.example.notify.domain.strategy.StrategyScope;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    private final StringRedisTemplate redis;

    public RedisCandidateIndex(StringRedisTemplate redis) {
        if (redis == null) {
            throw new IllegalArgumentException("StringRedisTemplate must not be null");
        }
        this.redis = redis;
    }

    /**
     * Add a strategy to the scope, event type, and field indexes.
     */
    public void index(StrategyId strategyId, StrategyScope scope, EventType eventType, List<RedisFieldIndex> fieldIndexes) {
        if (strategyId == null || scope == null || eventType == null || fieldIndexes == null) {
            throw new IllegalArgumentException("index state is incomplete");
        }
        String id = strategyId.value();
        indexScope(id, scope);
        redis.opsForSet().add(IDX_EVENT_TYPE + eventType.value(), id);
        for (RedisFieldIndex fi : fieldIndexes) {
            redis.opsForSet().add(IDX_FIELD + fi.field() + ":" + fi.value(), id);
        }
    }

    /**
     * Remove a strategy from the scope, event type, and field indexes.
     */
    public void remove(StrategyId strategyId, StrategyScope scope, EventType eventType, List<RedisFieldIndex> fieldIndexes) {
        if (strategyId == null || scope == null || eventType == null || fieldIndexes == null) {
            throw new IllegalArgumentException("remove state is incomplete");
        }
        String id = strategyId.value();
        removeScope(id, scope);
        redis.opsForSet().remove(IDX_EVENT_TYPE + eventType.value(), id);
        for (RedisFieldIndex fi : fieldIndexes) {
            redis.opsForSet().remove(IDX_FIELD + fi.field() + ":" + fi.value(), id);
        }
    }

    /**
     * Compute candidate strategy IDs for a given event context.
     * <p>
     * Algorithm: union of scope candidates, intersect with event type, intersect with each field index.
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

        // Step 3: intersect with each field index
        for (var entry : fields.entrySet()) {
            Set<String> fieldCandidates = redis.opsForSet().members(IDX_FIELD + entry.getKey() + ":" + entry.getValue());
            if (fieldCandidates != null) {
                scopeCandidates.retainAll(fieldCandidates);
            }
        }

        return scopeCandidates.stream()
                .map(StrategyId::new)
                .collect(java.util.stream.Collectors.toSet());
    }

    private void indexScope(String strategyId, StrategyScope scope) {
        if (scope.kind() == StrategyScope.Kind.GLOBAL) {
            redis.opsForSet().add(IDX_SCOPE_GLOBAL, strategyId);
        }
        for (UserId uid : scope.userIds()) {
            redis.opsForSet().add(IDX_SCOPE_USER + uid.value(), strategyId);
        }
        for (UserGroupId gid : scope.userGroupIds()) {
            redis.opsForSet().add(IDX_SCOPE_GROUP + gid.value(), strategyId);
        }
    }

    private void removeScope(String strategyId, StrategyScope scope) {
        if (scope.kind() == StrategyScope.Kind.GLOBAL) {
            redis.opsForSet().remove(IDX_SCOPE_GLOBAL, strategyId);
        }
        for (UserId uid : scope.userIds()) {
            redis.opsForSet().remove(IDX_SCOPE_USER + uid.value(), strategyId);
        }
        for (UserGroupId gid : scope.userGroupIds()) {
            redis.opsForSet().remove(IDX_SCOPE_GROUP + gid.value(), strategyId);
        }
    }
}
