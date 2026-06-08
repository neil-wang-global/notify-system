package com.example.notify.interfaces.kafka;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.example.notify.domain.event.CustomerId;
import com.example.notify.domain.event.EventId;
import com.example.notify.domain.event.EventType;
import com.example.notify.domain.event.UserId;
import com.example.notify.domain.notification.NotificationEvent;
import com.example.notify.domain.notification.NotificationId;
import com.example.notify.domain.notification.NotificationRecord;
import com.example.notify.domain.notification.NotificationRecords;
import com.example.notify.domain.strategy.StrategyId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "notify.kafka.enabled", havingValue = "true")
public class NotificationEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventsConsumer.class);

    private final NotificationRecords notificationRecords;
    private final ObjectMapper objectMapper;

    public NotificationEventsConsumer(NotificationRecords notificationRecords,
                                      ObjectMapper objectMapper) {
        this.notificationRecords = notificationRecords;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${notify.kafka.topics.notification-events}")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.debug("received notification-event offset={} key={}", record.offset(), record.key());
        try {
            JsonNode root = objectMapper.readTree(record.value());

            NotificationEvent event = new NotificationEvent(
                new NotificationId(root.path("notificationId").asText()),
                new StrategyId(root.path("strategyId").asText()),
                new CustomerId(root.path("customerId").asText()),
                new UserId(root.path("userId").asText()),
                new EventId(root.path("eventId").asText()),
                new EventType(root.path("eventType").asText()),
                Instant.parse(root.path("triggeredAt").asText()),
                root.path("window").asText(),
                root.path("threshold").asInt(),
                root.path("currentCount").asInt(),
                root.path("dedupeKey").asText()
            );

            notificationRecords.addIfAbsent(NotificationRecord.from(event));

            ack.acknowledge();
            log.debug("acked notification-event notificationId={}", event.notificationId());
        } catch (Exception e) {
            log.error("failed to process notification-event offset={}", record.offset(), e);
        }
    }

}
