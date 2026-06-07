package com.example.notify.infrastructure.redis;

import com.example.notify.domain.event.EventType;
import com.example.notify.domain.event.UserGroupId;
import com.example.notify.domain.event.UserId;
import com.example.notify.domain.strategy.StrategyExecutionPlan;
import com.example.notify.domain.strategy.StrategyId;
import com.example.notify.domain.strategy.StrategyScope;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class RedisStrategies {

    private final Map<StrategyId, RedisStrategy> strategies = new ConcurrentHashMap<>();
    private final ScopeIndex scopeIndex = new ScopeIndex();
    private final EventTypeIndex eventTypeIndex = new EventTypeIndex();
    private final FieldIndex fieldIndex = new FieldIndex();

    public boolean refresh(RedisStrategy strategy) {
        if (strategy == null) {
            throw new IllegalArgumentException("redis strategy must not be null");
        }
        RedisStrategy existing = strategies.get(strategy.strategyId());
        if (existing != null && strategy.version().value() < existing.version().value()) {
            return false;
        }
        if (existing != null) {
            scopeIndex.remove(existing.strategyId(), existing.scope());
            eventTypeIndex.remove(existing.eventType(), existing.strategyId());
            for (RedisFieldIndex index : existing.fieldIndexes()) {
                fieldIndex.remove(index.field(), index.value(), existing.strategyId());
            }
        }
        strategies.put(strategy.strategyId(), strategy);
        scopeIndex.add(strategy.strategyId(), strategy.scope());
        eventTypeIndex.add(strategy.eventType(), strategy.strategyId());
        for (RedisFieldIndex index : strategy.fieldIndexes()) {
            fieldIndex.add(index.field(), index.value(), strategy.strategyId());
        }
        return true;
    }

    public Optional<StrategyExecutionPlan> plan(StrategyId strategyId) {
        return Optional.ofNullable(strategies.get(strategyId)).map(RedisStrategy::executionPlan);
    }

    public ScopeIndex scopeIndex() {
        return scopeIndex;
    }

    public EventTypeIndex eventTypeIndex() {
        return eventTypeIndex;
    }

    public FieldIndex fieldIndex() {
        return fieldIndex;
    }

    public static final class ScopeIndex {
        private final Set<StrategyId> globalStrategies = ConcurrentHashMap.newKeySet();
        private final Map<UserId, Set<StrategyId>> userStrategies = new ConcurrentHashMap<>();
        private final Map<UserGroupId, Set<StrategyId>> groupStrategies = new ConcurrentHashMap<>();

        private void add(StrategyId strategyId, StrategyScope scope) {
            if (scope.kind() == StrategyScope.Kind.GLOBAL) {
                globalStrategies.add(strategyId);
            }
            for (UserId userId : scope.userIds()) {
                userStrategies.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(strategyId);
            }
            for (UserGroupId groupId : scope.userGroupIds()) {
                groupStrategies.computeIfAbsent(groupId, ignored -> ConcurrentHashMap.newKeySet()).add(strategyId);
            }
        }

        private void remove(StrategyId strategyId, StrategyScope scope) {
            if (scope.kind() == StrategyScope.Kind.GLOBAL) {
                globalStrategies.remove(strategyId);
            }
            for (UserId userId : scope.userIds()) {
                Set<StrategyId> ids = userStrategies.get(userId);
                if (ids != null) {
                    ids.remove(strategyId);
                }
            }
            for (UserGroupId groupId : scope.userGroupIds()) {
                Set<StrategyId> ids = groupStrategies.get(groupId);
                if (ids != null) {
                    ids.remove(strategyId);
                }
            }
        }

        public Set<StrategyId> globalStrategies() {
            return Set.copyOf(globalStrategies);
        }

        public Set<StrategyId> userStrategies(UserId userId) {
            return Set.copyOf(userStrategies.getOrDefault(userId, Set.of()));
        }

        public Set<StrategyId> groupStrategies(UserGroupId userGroupId) {
            return Set.copyOf(groupStrategies.getOrDefault(userGroupId, Set.of()));
        }
    }

    public static final class EventTypeIndex {
        private final Map<EventType, Set<StrategyId>> strategies = new ConcurrentHashMap<>();

        private void add(EventType eventType, StrategyId strategyId) {
            strategies.computeIfAbsent(eventType, ignored -> ConcurrentHashMap.newKeySet()).add(strategyId);
        }

        private void remove(EventType eventType, StrategyId strategyId) {
            Set<StrategyId> ids = strategies.get(eventType);
            if (ids != null) {
                ids.remove(strategyId);
            }
        }

        public Set<StrategyId> strategies(EventType eventType) {
            return Set.copyOf(strategies.getOrDefault(eventType, Set.of()));
        }
    }

    public static final class FieldIndex {
        private final Map<String, Set<StrategyId>> strategies = new ConcurrentHashMap<>();

        private void add(String field, String value, StrategyId strategyId) {
            strategies.computeIfAbsent(field + ':' + value, ignored -> ConcurrentHashMap.newKeySet()).add(strategyId);
        }

        private void remove(String field, String value, StrategyId strategyId) {
            Set<StrategyId> ids = strategies.get(field + ':' + value);
            if (ids != null) {
                ids.remove(strategyId);
            }
        }

        public Set<StrategyId> strategies(String field, String value) {
            return Set.copyOf(strategies.getOrDefault(field + ':' + value, Set.of()));
        }
    }

}
