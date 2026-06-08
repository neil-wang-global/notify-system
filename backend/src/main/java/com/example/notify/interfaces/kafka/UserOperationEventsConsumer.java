package com.example.notify.interfaces.kafka;

import com.example.notify.application.event.ProcessUserOperationEvent;
import com.example.notify.domain.event.EventId;
import com.example.notify.domain.strategy.Strategies;
import com.example.notify.domain.strategy.Strategy;
import com.example.notify.engine.matching.EventSnapshot;
import com.example.notify.infrastructure.redis.CandidateStrategyLookup;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
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

@Component
@ConditionalOnProperty(name = "notify.kafka.enabled", havingValue = "true")
public class UserOperationEventsConsumer {
    private static final Logger log = LoggerFactory.getLogger(UserOperationEventsConsumer.class);
    private final ProcessUserOperationEvent processUserOperationEvent;
    private final CandidateStrategyLookup candidateLookup;
    private final Strategies strategies;
    private final ObjectMapper objectMapper;

    public UserOperationEventsConsumer(ProcessUserOperationEvent processUserOperationEvent, CandidateStrategyLookup candidateLookup, Strategies strategies, ObjectMapper objectMapper) {
        this.processUserOperationEvent = processUserOperationEvent;
        this.candidateLookup = candidateLookup;
        this.strategies = strategies;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${notify.kafka.topics.user-operation-events}")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.debug("received user-operation-event offset={} key={}", record.offset(), record.key());
        try {
            JsonNode root = objectMapper.readTree(record.value());
            String eventId = root.path("eventId").asText();
            String customerId = root.path("customerId").asText();
            String userId = root.path("userId").asText();
            String eventType = root.path("eventType").asText();
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
                .map(UserOperationEventsConsumer::toStrategy).toList();
            processUserOperationEvent.process(new EventId(eventId), snapshot, matchedStrategies, occurredAt);
            ack.acknowledge();
            log.debug("acked user-operation-event eventId={}", eventId);
        } catch (Exception e) { log.error("failed to process user-operation-event offset={}", record.offset(), e); throw new RuntimeException(e); }
    }

    private static ProcessUserOperationEvent.MatchedStrategy toStrategy(Strategy s) {
        return new ProcessUserOperationEvent.MatchedStrategy(s.id(), s.ruleAst(), s.executionPlan(), s.threshold());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> collectFields(JsonNode fieldsNode) {
        return fieldsNode.fields().hasNext()
            ? java.util.stream.StreamSupport.stream(((Iterable<Map.Entry<String, JsonNode>>) () -> fieldsNode.fields()).spliterator(), false)
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().asText()))
            : Map.of();
    }
}
