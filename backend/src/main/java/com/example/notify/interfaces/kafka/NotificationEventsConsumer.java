package com.example.notify.interfaces.kafka;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.example.notify.application.notification.PersistNotification;
import com.example.notify.domain.event.CustomerId;
import com.example.notify.domain.event.EventId;
import com.example.notify.domain.event.EventType;
import com.example.notify.domain.event.UserId;
import com.example.notify.domain.notification.NotificationEvent;
import com.example.notify.domain.notification.NotificationId;
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

    private final PersistNotification persistNotification;
    private final ObjectMapper objectMapper;

    public NotificationEventsConsumer(PersistNotification persistNotification,
                                      ObjectMapper objectMapper) {
        if (persistNotification == null) { throw new IllegalArgumentException("persistNotification must not be null"); }
        if (objectMapper == null) { throw new IllegalArgumentException("objectMapper must not be null"); }
        this.persistNotification = persistNotification;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${notify.kafka.topics.notification-events}")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.debug("received notification-event offset={} key={}", record.offset(), record.key());
        try {
            JsonNode root = objectMapper.readTree(record.value());

            NotificationEvent event = new NotificationEvent(
                new NotificationId(extractText(root.path("notificationId"))),
                new StrategyId(extractText(root.path("strategyId"))),
                new CustomerId(extractText(root.path("customerId"))),
                new UserId(extractText(root.path("userId"))),
                new EventId(extractText(root.path("eventId"))),
                new EventType(extractText(root.path("eventType"))),
                Instant.parse(extractText(root.path("triggeredAt"))),
                extractText(root.path("window")),
                root.path("threshold").asInt(),
                root.path("currentCount").asInt(),
                extractText(root.path("dedupeKey"))
            );

            persistNotification.persist(event);

            ack.acknowledge();
            log.debug("acked notification-event notificationId={}", event.notificationId());
        } catch (Exception e) {
            log.error("failed to process notification-event offset={}", record.offset(), e);
            throw new RuntimeException(e);
        }
    }

    private static String extractText(JsonNode node) {
        if (node.isObject() && node.has("value")) {
            return node.get("value").asText();
        }
        return node.asText();
    }

}
