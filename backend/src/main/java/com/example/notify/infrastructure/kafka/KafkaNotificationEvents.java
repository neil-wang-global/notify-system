package com.example.notify.infrastructure.kafka;

import com.example.notify.domain.notification.NotificationEvent;
import com.example.notify.domain.notification.NotificationEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "notify.kafka.enabled", havingValue = "true")
public class KafkaNotificationEvents implements NotificationEvents {

    private static final Logger log = LoggerFactory.getLogger(KafkaNotificationEvents.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public KafkaNotificationEvents(KafkaTemplate<String, String> kafkaTemplate,
                                   com.example.notify.config.NotifyProperties props,
                                   com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = props.kafka().topics().notificationEvents();
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(NotificationEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, event.customerId().value(), payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("failed to send notification event to Kafka topic={}, customerId={}", topic, event.customerId().value(), ex);
                    } else if (log.isDebugEnabled()) {
                        log.debug("sent notification event to Kafka topic={}, partition={}, offset={}",
                            topic, result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                    }
                });
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("failed to serialize notification event", e);
        }
    }

}
