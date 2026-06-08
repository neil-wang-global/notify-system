package com.example.notify.infrastructure.redis;

import com.example.notify.engine.timebox.TimeboxCommand;
import com.example.notify.engine.timebox.TimeboxOperations;
import com.example.notify.engine.timebox.TimeboxResult;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * Redis-backed timebox counter using a Lua script for atomic event idempotency,
 * business dedup, bucket-based counting, window sum, TTL, and threshold decision.
 * <p>
 * Key layout:
 * <ul>
 *   <li>{@code processed:{eventId}} — event idempotency marker</li>
 *   <li>{@code dedup:{strategyId}:{dedupHash}} — business dedup window marker</li>
 *   <li>{@code timebox:{windowKey}} — hash of bucket timestamps to counts</li>
 * </ul>
 */
public final class RedisTimeboxCounter implements TimeboxOperations {

    private static final String PROCESSED_PREFIX = "processed:";
    private static final String DEDUP_PREFIX = "dedup:";
    private static final String TIMEBOX_PREFIX = "timebox:";

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<List> timeboxScript;

    public RedisTimeboxCounter(StringRedisTemplate redis) {
        if (redis == null) {
            throw new IllegalArgumentException("StringRedisTemplate must not be null");
        }
        this.redis = redis;
        this.timeboxScript = loadScript();
    }

    @Override
    public TimeboxResult apply(TimeboxCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("timebox command must not be null");
        }

        long shardMs = command.shardSize().toMillis();
        long windowMs = command.windowSize().toMillis();
        long ttlMs = windowMs + shardMs * 2;
        long bucketTs = (command.occurredAt().toEpochMilli() / shardMs) * shardMs;

        String processedKey = PROCESSED_PREFIX + command.eventId().value();
        String dedupKey = DEDUP_PREFIX + command.dedupKey();
        String timeboxKey = TIMEBOX_PREFIX + command.windowKey();

        List result = redis.execute(
                timeboxScript,
                List.of(processedKey, dedupKey, timeboxKey),
                command.eventId().value(),
                String.valueOf(command.businessDedupWindow().toMillis()),
                String.valueOf(bucketTs),
                String.valueOf(windowMs),
                String.valueOf(shardMs),
                String.valueOf(command.threshold()),
                String.valueOf(ttlMs)
        );

        if (result == null || result.size() < 3) {
            throw new IllegalStateException("unexpected Lua script result: " + result);
        }

        boolean triggered = toLong(result.get(0)) == 1L;
        int currentCount = (int) toLong(result.get(1));
        return new TimeboxResult(triggered, currentCount);
    }

    @SuppressWarnings("unchecked")
    private static DefaultRedisScript<List> loadScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/timebox.lua"));
        script.setResultType(List.class);
        return script;
    }

    private static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

}
