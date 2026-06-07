package com.example.notify.engine.matching;

import com.example.notify.domain.event.EventType;
import com.example.notify.domain.event.UserGroupId;
import com.example.notify.domain.event.UserId;
import com.example.notify.domain.strategy.StrategyId;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class CandidateStrategyIndex {

    private final Set<StrategyId> globalStrategies = ConcurrentHashMap.newKeySet();
    private final Map<UserId, Set<StrategyId>> userStrategies = new ConcurrentHashMap<>();
    private final Map<UserGroupId, Set<StrategyId>> groupStrategies = new ConcurrentHashMap<>();
    private final Map<EventType, Set<StrategyId>> eventTypeStrategies = new ConcurrentHashMap<>();
    private final Map<String, Set<StrategyId>> fieldStrategies = new ConcurrentHashMap<>();

    public void addGlobal(StrategyId strategyId) {
        globalStrategies.add(strategyId);
    }

    public void addUser(UserId userId, StrategyId strategyId) {
        userStrategies.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(strategyId);
    }

    public void addGroup(UserGroupId groupId, StrategyId strategyId) {
        groupStrategies.computeIfAbsent(groupId, ignored -> ConcurrentHashMap.newKeySet()).add(strategyId);
    }

    public void addEventType(EventType eventType, StrategyId... strategyIds) {
        eventTypeStrategies.computeIfAbsent(eventType, ignored -> ConcurrentHashMap.newKeySet()).addAll(Arrays.asList(strategyIds));
    }

    public void addField(String field, String value, StrategyId... strategyIds) {
        fieldStrategies.computeIfAbsent(field + ':' + value, ignored -> ConcurrentHashMap.newKeySet()).addAll(Arrays.asList(strategyIds));
    }

    public Set<StrategyId> candidates(EventSnapshot snapshot) {
        Set<StrategyId> scopeCandidates = new HashSet<>(globalStrategies);
        scopeCandidates.addAll(userStrategies.getOrDefault(new UserId(snapshot.userId()), Set.of()));
        for (String groupId : snapshot.userGroupIds()) {
            scopeCandidates.addAll(groupStrategies.getOrDefault(new UserGroupId(groupId), Set.of()));
        }

        Set<StrategyId> eventCandidates = eventTypeStrategies.getOrDefault(new EventType(snapshot.eventType()), Set.of());
        scopeCandidates.retainAll(eventCandidates);
        for (Map.Entry<String, String> field : snapshot.fields().entrySet()) {
            Set<StrategyId> fieldCandidates = fieldStrategies.get(field.getKey() + ':' + field.getValue());
            if (fieldCandidates != null) {
                scopeCandidates.retainAll(fieldCandidates);
            }
        }
        return Set.copyOf(scopeCandidates);
    }

}
