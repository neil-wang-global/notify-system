package com.example.notify.interfaces.rest;

import com.example.notify.application.event.ProcessUserOperationEvent;
import com.example.notify.domain.event.EventId;
import com.example.notify.engine.matching.EventSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EventsApi {

    private final ProcessUserOperationEvent processUserOperationEvent;
    private final List<ProcessUserOperationEvent.MatchedStrategy> matchedStrategies;

    public EventsApi(ProcessUserOperationEvent processUserOperationEvent, List<ProcessUserOperationEvent.MatchedStrategy> matchedStrategies) {
        if (processUserOperationEvent == null || matchedStrategies == null) {
            throw new IllegalArgumentException("events api is incomplete");
        }
        this.processUserOperationEvent = processUserOperationEvent;
        this.matchedStrategies = List.copyOf(matchedStrategies);
    }

    public EventResponse simulate(UserOperationEventRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("event request must not be null");
        }
        processUserOperationEvent.process(
            new EventId(request.eventId()),
            new EventSnapshot(request.customerId(), request.userId(), Set.copyOf(request.userGroupIds()), request.eventType(), request.fields()),
            matchedStrategies,
            request.occurredAt()
        );
        return new EventResponse(request.eventId(), matchedStrategies.size());
    }

    public record UserOperationEventRequest(
        String eventId,
        String customerId,
        String userId,
        List<String> userGroupIds,
        String eventType,
        Map<String, String> fields,
        Instant occurredAt
    ) {

        public UserOperationEventRequest {
            userGroupIds = userGroupIds == null ? List.of() : List.copyOf(userGroupIds);
            fields = fields == null ? Map.of() : Map.copyOf(fields);
            occurredAt = occurredAt == null ? Instant.now() : occurredAt;
        }

    }

    public record EventResponse(String eventId, int candidateStrategies) {

    }

}
