package com.example.notify.interfaces.kafka;

import com.example.notify.application.event.ProcessUserOperationEvent;
import com.example.notify.config.DegradationState;
import com.example.notify.domain.event.EventId;
import com.example.notify.domain.strategy.Strategies;
import com.example.notify.engine.matching.EventSnapshot;
import com.example.notify.engine.matching.CandidateStrategyLookup;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Non-recoverable exceptions (JSON parsing, missing required fields) are logged and acknowledged
 * so the message is consumed without retry. Transient errors (Redis timeout, etc.) are re-thrown
 * to trigger retry via the error handler.
 */
@Component
@ConditionalOnProperty(name = "notify.kafka.enabled", havingValue = "true")
public class UserOperationEventsConsumer {
    private static final Logger log = LoggerFactory.getLogger(UserOperationEventsConsumer.class);
    private final ProcessUserOperationEvent processUserOperationEvent;
    private final CandidateStrategyLookup candidateLookup;
    private final Strategies strategies;
    private final ObjectMapper objectMapper;
    private final DegradationState degradationState;

    public UserOperationEventsConsumer(ProcessUserOperationEvent processUserOperationEvent, CandidateStrategyLookup candidateLookup, Strategies strategies, ObjectMapper objectMapper, DegradationState degradationState) {
        this.processUserOperationEvent = processUserOperationEvent;
        this.candidateLookup = candidateLookup;
        this.strategies = strategies;
        this.objectMapper = objectMapper;
        this.degradationState = degradationState;
    }

    @KafkaListener(topics = "${notify.kafka.topics.user-operation-events}")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.debug("received user-operation-event offset={} key={}", record.offset(), record.key());

        // T-14: fail-fast when degraded — throw before doing any work
        if (degradationState.isDegraded()) {
            log.warn("system degraded ({}), refusing to process offset={} — triggering retry", degradationState.reason(), record.offset());
            throw new RuntimeException("System degraded: " + degradationState.reason());
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(record.value());
        } catch (JsonParseException e) {
            // T-11: non-recoverable — bad JSON, no point retrying
            log.error("non-recoverable: invalid JSON at offset={}, acking without retry", record.offset(), e);
            ack.acknowledge();
            return;
        } catch (Exception e) {
            // Other IO issues during parsing — treat as transient
            throw new RuntimeException("failed to parse user-operation-event at offset=" + record.offset(), e);
        }

        // Validate required fields
        String eventId = root.path("eventId").asText();
        String customerId = root.path("customerId").asText();
        String userId = root.path("userId").asText();
        String eventType = root.path("eventType").asText();
        if (eventId.isEmpty() || customerId.isEmpty() || userId.isEmpty() || eventType.isEmpty()) {
            // T-11: non-recoverable — missing required fields
            log.error("non-recoverable: missing required fields (eventId={}, customerId={}, userId={}, eventType={}) at offset={}, acking without retry",
                eventId, customerId, userId, eventType, record.offset());
            ack.acknowledge();
            return;
        }

        try {
            Set<String> userGroupIds = new HashSet<>();
            JsonNode groupsNode = root.path("userGroupIds");
            if (groupsNode.isArray()) { for (JsonNode g : groupsNode) { userGroupIds.add(g.asText()); } }
            Map<String, String> fields = Map.of();
            JsonNode fieldsNode = root.path("fields");
            if (fieldsNode.isObject()) { fields = collectFields(fieldsNode); }
            Instant occurredAt = root.path("occurredAt").isTextual() ? Instant.parse(root.path("occurredAt").asText()) : Instant.now();
            EventSnapshot snapshot = new EventSnapshot(customerId, userId, userGroupIds, eventType, fields);
            Set<com.example.notify.domain.strategy.StrategyId> candidateIds = candidateLookup.candidates(customerId, userId, userGroupIds, eventType, fields);
            List<ProcessUserOperationEvent.MatchedStrategy> matchedStrategies = candidateIds.stream()
                .map(id -> strategies.find(id)).filter(java.util.Optional::isPresent).map(java.util.Optional::get)
                .map(ProcessUserOperationEvent.MatchedStrategy::from).toList();
            processUserOperationEvent.process(new EventId(eventId), snapshot, matchedStrategies, occurredAt);
            ack.acknowledge();
            log.debug("acked user-operation-event eventId={}", eventId);
        } catch (Exception e) {
            log.error("failed to process user-operation-event offset={}", record.offset(), e);
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> collectFields(JsonNode fieldsNode) {
        return fieldsNode.fields().hasNext()
            ? java.util.stream.StreamSupport.stream(((Iterable<Map.Entry<String, JsonNode>>) () -> fieldsNode.fields()).spliterator(), false)
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().asText()))
            : Map.of();
    }
}
