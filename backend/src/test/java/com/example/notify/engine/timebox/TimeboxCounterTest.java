package com.example.notify.engine.timebox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.notify.domain.event.CustomerId;
import com.example.notify.domain.event.EventId;
import com.example.notify.domain.strategy.StrategyId;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TimeboxCounterTest {

    @Test
    void duplicateEventIdDoesNotCountTwice() {
        TimeboxCounter counter = new TimeboxCounter();
        TimeboxCommand command = command("event-1", "dedup-1", Instant.parse("2026-06-07T00:00:00Z"));

        TimeboxResult first = counter.apply(command);
        TimeboxResult duplicate = counter.apply(command);

        assertEquals(1, first.currentCount());
        assertEquals(1, duplicate.currentCount());
        assertFalse(duplicate.triggered());
    }

    @Test
    void businessDedupWindowFoldsRepeatedClicks() {
        TimeboxCounter counter = new TimeboxCounter();

        TimeboxResult first = counter.apply(command("event-1", "same-click", Instant.parse("2026-06-07T00:00:00Z")));
        TimeboxResult repeatedClick = counter.apply(command("event-2", "same-click", Instant.parse("2026-06-07T00:00:05Z")));
        TimeboxResult outsideWindow = counter.apply(command("event-3", "same-click", Instant.parse("2026-06-07T00:00:11Z")));

        assertEquals(1, first.currentCount());
        assertEquals(1, repeatedClick.currentCount());
        assertEquals(2, outsideWindow.currentCount());
    }

    @Test
    void bucketRolloverKeepsOnlyWindowBucketsAndTriggersThreshold() {
        TimeboxCounter counter = new TimeboxCounter();

        counter.apply(command("event-1", "click-1", Instant.parse("2026-06-07T00:00:00Z")));
        counter.apply(command("event-2", "click-2", Instant.parse("2026-06-07T00:00:11Z")));
        TimeboxResult triggered = counter.apply(command("event-3", "click-3", Instant.parse("2026-06-07T00:00:21Z")));
        TimeboxResult rolled = counter.apply(command("event-4", "click-4", Instant.parse("2026-06-07T00:01:01Z")));

        assertTrue(triggered.triggered());
        assertEquals(3, triggered.currentCount());
        assertEquals(1, rolled.currentCount());
    }

    private static TimeboxCommand command(String eventId, String dedupHash, Instant occurredAt) {
        return new TimeboxCommand(
            new StrategyId("strategy-1"),
            new CustomerId("customer-1"),
            dedupHash,
            new EventId(eventId),
            occurredAt,
            Duration.ofSeconds(30),
            Duration.ofSeconds(10),
            Duration.ofSeconds(10),
            3
        );
    }

}
