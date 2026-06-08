package com.example.notify.interfaces.rest;

import com.example.notify.application.event.ProcessUserOperationEvent;
import com.example.notify.domain.event.EventId;
import com.example.notify.domain.strategy.Strategies;
import com.example.notify.domain.strategy.Strategy;
import com.example.notify.engine.matching.EventSnapshot;
import com.example.notify.infrastructure.redis.CandidateStrategyLookup;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public final class EventsApi {
    private final ProcessUserOperationEvent processUserOperationEvent;
    private final CandidateStrategyLookup candidateLookup;
    private final Strategies strategies;

    public EventsApi(ProcessUserOperationEvent processUserOperationEvent, CandidateStrategyLookup candidateLookup, Strategies strategies) {
        if (processUserOperationEvent == null || candidateLookup == null || strategies == null) { throw new IllegalArgumentException("events api is incomplete"); }
        this.processUserOperationEvent = processUserOperationEvent;
        this.candidateLookup = candidateLookup;
        this.strategies = strategies;
    }

    @PostMapping("/simulate")
    public EventResponse simulate(@RequestBody UserOperationEventRequest request) {
        if (request == null) { throw new IllegalArgumentException("event request must not be null"); }
        EventSnapshot snapshot = new EventSnapshot(request.customerId(), request.userId(), Set.copyOf(request.userGroupIds()), request.eventType(), request.fields());
        Set<com.example.notify.domain.strategy.StrategyId> candidateIds = candidateLookup.candidates(request.customerId(), request.userId(), Set.copyOf(request.userGroupIds()), request.eventType(), request.fields());
        List<ProcessUserOperationEvent.MatchedStrategy> matchedStrategies = candidateIds.stream()
            .map(id -> strategies.find(id)).filter(java.util.Optional::isPresent).map(java.util.Optional::get)
            .map(EventsApi::toStrategy).toList();
        processUserOperationEvent.process(new EventId(request.eventId()), snapshot, matchedStrategies, request.occurredAt());
        return new EventResponse(request.eventId(), matchedStrategies.size());
    }

    private static ProcessUserOperationEvent.MatchedStrategy toStrategy(Strategy s) {
        return new ProcessUserOperationEvent.MatchedStrategy(s.id(), s.ruleAst(), s.executionPlan(), s.threshold());
    }

    public record UserOperationEventRequest(String eventId, String customerId, String userId, List<String> userGroupIds, String eventType, Map<String, String> fields, Instant occurredAt) {
        public UserOperationEventRequest { userGroupIds = userGroupIds == null ? List.of() : List.copyOf(userGroupIds); fields = fields == null ? Map.of() : Map.copyOf(fields); occurredAt = occurredAt == null ? Instant.now() : occurredAt; }
    }
    public record EventResponse(String eventId, int candidateStrategies) {}
}
