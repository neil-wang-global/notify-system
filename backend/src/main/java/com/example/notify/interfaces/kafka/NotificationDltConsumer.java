package com.example.notify.interfaces.kafka;

import com.example.notify.domain.event.CustomerId;
import com.example.notify.domain.event.EventId;
import com.example.notify.domain.notification.NotificationId;
import com.example.notify.domain.strategy.StrategyId;
import com.example.notify.domain.exception.NotificationExceptionRecord;
import com.example.notify.domain.exception.NotificationExceptions;
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
public class NotificationDltConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationDltConsumer.class);

    private final NotificationExceptions notificationExceptions;
    private final ObjectMapper objectMapper;

    public NotificationDltConsumer(NotificationExceptions notificationExceptions,
                                   ObjectMapper objectMapper) {
        this.notificationExceptions = notificationExceptions;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${notify.kafka.topics.notification-events-dlt}")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.warn("received notification-event from DLT offset={} key={}", record.offset(), record.key());
        try {
            JsonNode root = objectMapper.readTree(record.value());

            String notificationId = root.path("notificationId").asText();
            String strategyId = root.path("strategyId").asText();
            String customerId = root.path("customerId").asText();
            String eventId = root.path("eventId").asText();

            Instant now = Instant.now();
            NotificationExceptionRecord exceptionRecord = new NotificationExceptionRecord(
                UUID.randomUUID().toString(),
                new NotificationId(notificationId),
                new StrategyId(strategyId),
                new CustomerId(customerId),
                new EventId(eventId),
                record.value(),
                "exhausted retries",
                3,
                "DEAD",
                now,
                now
            );

            notificationExceptions.add(exceptionRecord);
            ack.acknowledge();
            log.info("persisted notification exception from DLT notificationId={}", notificationId);
        } catch (Exception e) {
            log.error("failed to process notification DLT message offset={}", record.offset(), e);
        }
    }

}
