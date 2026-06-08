package com.example.notify.infrastructure.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.notify.domain.event.CustomerId;
import com.example.notify.domain.event.EventId;
import com.example.notify.domain.strategy.StrategyId;
import com.example.notify.engine.timebox.TimeboxCommand;
import com.example.notify.engine.timebox.TimeboxResult;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.datasource.url=jdbc:h2:mem:notify-timebox-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.sql.init.mode=never"
})
@Testcontainers
@Tag("docker")
class RedisTimeboxCounterIT {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private StringRedisTemplate redisTemplate;

    private RedisTimeboxCounter counter;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        counter = new RedisTimeboxCounter(redisTemplate);
    }

    @Test
    void duplicateEventIdReturnsNotTriggered() {
        TimeboxCommand command = command("event-1", "dedup-1", Instant.parse("2026-06-07T00:00:00Z"));

        TimeboxResult first = counter.apply(command);
        TimeboxResult duplicate = counter.apply(command);

        assertEquals(1, first.currentCount());
        assertFalse(first.triggered());
        assertEquals(0, duplicate.currentCount());
        assertFalse(duplicate.triggered());
    }

    @Test
    void businessDedupWindowPreventsRecount() {
        TimeboxResult first = counter.apply(command("event-1", "same-click", Instant.parse("2026-06-07T00:00:00Z")));
        TimeboxResult repeatedClick = counter.apply(command("event-2", "same-click", Instant.parse("2026-06-07T00:00:05Z")));
        TimeboxResult outsideWindow = counter.apply(command("event-3", "same-click", Instant.parse("2026-06-07T00:00:11Z")));

        assertEquals(1, first.currentCount());
        assertEquals(0, repeatedClick.currentCount());
        assertFalse(repeatedClick.triggered());
        assertEquals(2, outsideWindow.currentCount());
    }

    @Test
    void bucketIncrementAndWindowSum() {
        TimeboxResult first = counter.apply(command("event-1", "click-1", Instant.parse("2026-06-07T00:00:00Z")));
        TimeboxResult second = counter.apply(command("event-2", "click-2", Instant.parse("2026-06-07T00:00:05Z")));

        assertEquals(1, first.currentCount());
        assertEquals(2, second.currentCount());
    }

    @Test
    void thresholdTriggersNotification() {
        counter.apply(command("event-1", "click-1", Instant.parse("2026-06-07T00:00:00Z")));
        counter.apply(command("event-2", "click-2", Instant.parse("2026-06-07T00:00:05Z")));
        TimeboxResult triggered = counter.apply(command("event-3", "click-3", Instant.parse("2026-06-07T00:00:10Z")));

        assertTrue(triggered.triggered());
        assertEquals(3, triggered.currentCount());
    }

    @Test
    void ttlPreventsKeyLeak() {
        counter.apply(command("event-1", "click-1", Instant.parse("2026-06-07T00:00:00Z")));

        String timeboxKey = "timebox:" + new StrategyId("strategy-1") + ":" + new CustomerId("customer-1");
        Long ttl = redisTemplate.getExpire(timeboxKey, TimeUnit.MILLISECONDS);
        assertTrue(ttl != null && ttl > 0, "timebox key should have a TTL set");

        String processedKey = "processed:" + "event-1";
        Long processedTtl = redisTemplate.getExpire(processedKey, TimeUnit.MILLISECONDS);
        assertTrue(processedTtl != null && processedTtl > 0, "processed key should have a TTL set");
    }

    @Test
    void bucketRolloverKeepsOnlyWindowBucketsAndTriggersThreshold() {
        counter.apply(command("event-1", "click-1", Instant.parse("2026-06-07T00:00:00Z")));
        counter.apply(command("event-2", "click-2", Instant.parse("2026-06-07T00:00:11Z")));
        TimeboxResult triggered = counter.apply(command("event-3", "click-3", Instant.parse("2026-06-07T00:00:21Z")));
        TimeboxResult rolled = counter.apply(command("event-4", "click-4", Instant.parse("2026-06-07T00:01:01Z")));

        assertTrue(triggered.triggered());
        assertEquals(3, triggered.currentCount());
        assertEquals(1, rolled.currentCount());
    }

    @Test
    void concurrentEventsNoLostCounts() throws Exception {
        int numThreads = 10;
        int eventsPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<TimeboxResult>> futures = new ArrayList<>();

        for (int t = 0; t < numThreads; t++) {
            final int threadIndex = t;
            futures.add(executor.submit(() -> {
                startLatch.await(5, TimeUnit.SECONDS);
                TimeboxResult last = null;
                for (int i = 0; i < eventsPerThread; i++) {
                    String eventId = "event-thread" + threadIndex + "-seq" + i;
                    last = counter.apply(command(eventId, "click-" + eventId, Instant.parse("2026-06-07T00:00:05Z")));
                }
                return last;
            }));
        }

        startLatch.countDown();

        int maxCount = 0;
        for (Future<TimeboxResult> future : futures) {
            TimeboxResult result = future.get(30, TimeUnit.SECONDS);
            if (result.currentCount() > maxCount) {
                maxCount = result.currentCount();
            }
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

        assertEquals(numThreads * eventsPerThread, maxCount,
                "no events should be lost under concurrent writes");
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
