package com.example.notify.infrastructure.redis;

import com.example.notify.domain.event.EventType;
import com.example.notify.domain.event.UserGroupId;
import com.example.notify.domain.event.UserId;
import com.example.notify.domain.strategy.Strategy;
import com.example.notify.domain.strategy.StrategyExecutionPlan;
import com.example.notify.domain.strategy.StrategyId;
import com.example.notify.domain.strategy.StrategyScope;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class RedisStrategies implements CandidateStrategyLookup {
    private final Map<StrategyId, RedisStrategy> strategies = new ConcurrentHashMap<>();
    private final ScopeIndex scopeIndex = new ScopeIndex();
    private final EventTypeIndex eventTypeIndex = new EventTypeIndex();
    private final FieldIndex fieldIndex = new FieldIndex();

    @Override
    public boolean refresh(Strategy strategy) {
        if (strategy == null) { throw new IllegalArgumentException("strategy must not be null"); }
        RedisStrategy rs = RedisStrategy.from(strategy);
        RedisStrategy existing = strategies.get(rs.strategyId());
        if (existing != null && rs.version().value() < existing.version().value()) { return false; }
        if (existing != null) {
            scopeIndex.remove(existing.strategyId(), existing.scope());
            eventTypeIndex.remove(existing.eventType(), existing.strategyId());
            for (RedisFieldIndex fi : existing.fieldIndexes()) { fieldIndex.remove(fi.field(), fi.value(), existing.strategyId()); }
        }
        strategies.put(rs.strategyId(), rs);
        scopeIndex.add(rs.strategyId(), rs.scope());
        eventTypeIndex.add(rs.eventType(), rs.strategyId());
        for (RedisFieldIndex fi : rs.fieldIndexes()) { fieldIndex.add(fi.field(), fi.value(), rs.strategyId()); }
        return true;
    }

    @Override
    public Optional<StrategyExecutionPlan> plan(StrategyId strategyId) {
        return Optional.ofNullable(strategies.get(strategyId)).map(RedisStrategy::executionPlan);
    }

    @Override
    public Set<StrategyId> candidates(String customerId, String userId, Set<String> userGroupIds, String eventType, Map<String, String> fields) {
        if (userId == null || userId.isBlank() || eventType == null || eventType.isBlank()) { throw new IllegalArgumentException("candidate lookup identity is incomplete"); }
        if (userGroupIds == null || fields == null) { throw new IllegalArgumentException("candidate lookup collections must not be null"); }
        Set<StrategyId> scopeCandidates = new HashSet<>(scopeIndex.globalStrategies());
        scopeCandidates.addAll(scopeIndex.userStrategies(new UserId(userId)));
        for (String gid : userGroupIds) { scopeCandidates.addAll(scopeIndex.groupStrategies(new UserGroupId(gid))); }
        scopeCandidates.retainAll(eventTypeIndex.strategies(new EventType(eventType)));
        for (Map.Entry<String, String> e : fields.entrySet()) { scopeCandidates.retainAll(fieldIndex.strategies(e.getKey(), e.getValue())); }
        return Set.copyOf(scopeCandidates);
    }

    public ScopeIndex scopeIndex() { return scopeIndex; }
    public EventTypeIndex eventTypeIndex() { return eventTypeIndex; }
    public FieldIndex fieldIndex() { return fieldIndex; }

    public static final class ScopeIndex {
        private final Set<StrategyId> globalStrategies = ConcurrentHashMap.newKeySet();
        private final Map<UserId, Set<StrategyId>> userStrategies = new ConcurrentHashMap<>();
        private final Map<UserGroupId, Set<StrategyId>> groupStrategies = new ConcurrentHashMap<>();
        private void add(StrategyId id, StrategyScope scope) {
            if (scope.kind() == StrategyScope.Kind.GLOBAL) { globalStrategies.add(id); }
            for (UserId uid : scope.userIds()) { userStrategies.computeIfAbsent(uid, k -> ConcurrentHashMap.newKeySet()).add(id); }
            for (UserGroupId gid : scope.userGroupIds()) { groupStrategies.computeIfAbsent(gid, k -> ConcurrentHashMap.newKeySet()).add(id); }
        }
        private void remove(StrategyId id, StrategyScope scope) {
            if (scope.kind() == StrategyScope.Kind.GLOBAL) { globalStrategies.remove(id); }
            for (UserId uid : scope.userIds()) { Set<StrategyId> s = userStrategies.get(uid); if (s != null) s.remove(id); }
            for (UserGroupId gid : scope.userGroupIds()) { Set<StrategyId> s = groupStrategies.get(gid); if (s != null) s.remove(id); }
        }
        public Set<StrategyId> globalStrategies() { return Set.copyOf(globalStrategies); }
        public Set<StrategyId> userStrategies(UserId uid) { return Set.copyOf(userStrategies.getOrDefault(uid, Set.of())); }
        public Set<StrategyId> groupStrategies(UserGroupId gid) { return Set.copyOf(groupStrategies.getOrDefault(gid, Set.of())); }
    }
    public static final class EventTypeIndex {
        private final Map<EventType, Set<StrategyId>> strategies = new ConcurrentHashMap<>();
        private void add(EventType et, StrategyId id) { strategies.computeIfAbsent(et, k -> ConcurrentHashMap.newKeySet()).add(id); }
        private void remove(EventType et, StrategyId id) { Set<StrategyId> s = strategies.get(et); if (s != null) s.remove(id); }
        public Set<StrategyId> strategies(EventType et) { return Set.copyOf(strategies.getOrDefault(et, Set.of())); }
    }
    public static final class FieldIndex {
        private final Map<String, Set<StrategyId>> strategies = new ConcurrentHashMap<>();
        private void add(String f, String v, StrategyId id) { strategies.computeIfAbsent(f+':'+v, k -> ConcurrentHashMap.newKeySet()).add(id); }
        private void remove(String f, String v, StrategyId id) { Set<StrategyId> s = strategies.get(f+':'+v); if (s != null) s.remove(id); }
        public Set<StrategyId> strategies(String f, String v) { return Set.copyOf(strategies.getOrDefault(f+':'+v, Set.of())); }
    }
}
