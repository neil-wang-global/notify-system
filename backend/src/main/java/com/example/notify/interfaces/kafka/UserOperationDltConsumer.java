package com.example.notify.interfaces.kafka;

import com.example.notify.domain.event.CustomerId;
import com.example.notify.domain.event.EventId;
import com.example.notify.domain.event.EventType;
import com.example.notify.domain.exception.UserOperationExceptionRecord;
import com.example.notify.domain.exception.UserOperationExceptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "notify.kafka.enabled", havingValue = "true")
public class UserOperationDltConsumer {

    private static final Logger log = LoggerFactory.getLogger(UserOperationDltConsumer.class);

    private final UserOperationExceptions userOperationExceptions;
    private final ObjectMapper objectMapper;

    public UserOperationDltConsumer(UserOperationExceptions userOperationExceptions,
                                    ObjectMapper objectMapper) {
        this.userOperationExceptions = userOperationExceptions;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${notify.kafka.topics.user-operation-events-dlt}")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.warn("received user-operation-event from DLT offset={} key={}", record.offset(), record.key());
        try {
            JsonNode root = objectMapper.readTree(record.value());

            String eventId = root.path("eventId").asText();
            String customerId = root.path("customerId").asText();
            String eventType = root.path("eventType").asText();

            Instant now = Instant.now();
            UserOperationExceptionRecord exceptionRecord = new UserOperationExceptionRecord(
                UUID.randomUUID().toString(),
                new EventId(eventId),
                new CustomerId(customerId),
                new EventType(eventType),
                record.value(),
                "exhausted retries",
                3,
                "DEAD",
                now,
                now
            );

            userOperationExceptions.add(exceptionRecord);
            ack.acknowledge();
            log.info("persisted user-operation exception from DLT eventId={}", eventId);
        } catch (Exception e) {
            log.error("failed to process user-operation DLT message offset={}", record.offset(), e);
        }
    }

}
