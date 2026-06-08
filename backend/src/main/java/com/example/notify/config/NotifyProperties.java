package com.example.notify.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notify")
public record NotifyProperties(
    Kafka kafka,
    Deduplication deduplication,
    Window window
) {

    public record Kafka(
        Topics topics
    ) {}

    public record Topics(
        String userOperationEvents,
        String notificationEvents,
        String userOperationEventsDlt,
        String notificationEventsDlt
    ) {}

    public record Deduplication(boolean enabled, Duration defaultWindow, List<String> defaultDimensions) {}

    public record Window(Duration defaultSize, Duration defaultShardSize) {}

}
