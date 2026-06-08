package com.example.notify.interfaces.kafka;

import com.example.notify.domain.event.CustomerId;
import com.example.notify.domain.event.EventId;
import com.example.notify.domain.event.EventType;
import com.example.notify.domain.exception.UserOperationExceptionRecord;
import com.example.notify.domain.exception.UserOperationExceptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
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

            int retryCount = readDeliveryAttempt(record);
            String failureReason = readFailureReason(record);

            Instant now = Instant.now();
            UserOperationExceptionRecord exceptionRecord = new UserOperationExceptionRecord(
                UUID.randomUUID().toString(),
                new EventId(eventId),
                new CustomerId(customerId),
                new EventType(eventType),
                record.value(),
                failureReason,
                retryCount,
                "RETRY_EXHAUSTED",
                now,
                now
            );

            userOperationExceptions.add(exceptionRecord);
            ack.acknowledge();
            log.info("persisted user-operation exception from DLT eventId={}", eventId);
        } catch (Exception e) {
            log.error("failed to process user-operation DLT message offset={}", record.offset(), e);
            throw new RuntimeException(e);
        }
    }

    private int readDeliveryAttempt(ConsumerRecord<?, ?> record) {
        Header header = record.headers().lastHeader(KafkaHeaders.DELIVERY_ATTEMPT);
        if (header != null) {
            try {
                return Integer.parseInt(new String(header.value(), StandardCharsets.UTF_8));
            } catch (NumberFormatException e) {
                log.warn("failed to parse delivery-attempt header, using default", e);
            }
        }
        // Fallback: check for the raw header name in case the constant doesn't match
        Header rawHeader = record.headers().lastHeader("kafka_delivery-attempt");
        if (rawHeader != null) {
            try {
                return Integer.parseInt(new String(rawHeader.value(), StandardCharsets.UTF_8));
            } catch (NumberFormatException e) {
                log.warn("failed to parse raw kafka_delivery-attempt header, using default", e);
            }
        }
        return 1;
    }

    private String readFailureReason(ConsumerRecord<?, ?> record) {
        Header origTopic = record.headers().lastHeader(KafkaHeaders.ORIGINAL_TOPIC);
        if (origTopic != null) {
            String topic = new String(origTopic.value(), StandardCharsets.UTF_8);
            return "exhausted retries from topic " + topic;
        }
        Header rawOrigTopic = record.headers().lastHeader("kafka_original-topic");
        if (rawOrigTopic != null) {
            String topic = new String(rawOrigTopic.value(), StandardCharsets.UTF_8);
            return "exhausted retries from topic " + topic;
        }
        return "exhausted retries";
    }

}
