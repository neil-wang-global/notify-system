package com.example.notify.engine.timebox;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class TimeboxCounter implements TimeboxOperations {

    private static final long EVICTION_TTL_NANOS = 60L * 1_000_000_000L; // 1 minute
    private static final int EVICTION_THRESHOLD = 8192;

    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ExpirableInstant> dedupKeys = new ConcurrentHashMap<>();
    private final Map<String, Map<Long, Integer>> bucketsByKey = new ConcurrentHashMap<>();

    @Override
    public synchronized TimeboxResult apply(TimeboxCommand command) {
        Map<Long, Integer> buckets = bucketsByKey.computeIfAbsent(command.windowKey(), ignored -> new ConcurrentHashMap<>());
        removeExpiredBuckets(buckets, command);

        String processedKey = command.windowKey() + ':' + command.eventId();
        if (isProcessed(processedKey)) {
            return new TimeboxResult(false, sum(buckets));
        }
        long expiryNanos = System.nanoTime() + EVICTION_TTL_NANOS;
        processedEvents.put(processedKey, expiryNanos);

        String dedupKey = command.dedupKey();
        ExpirableInstant lastAccepted = dedupKeys.get(dedupKey);
        if (lastAccepted != null && lastAccepted.notExpired()
                && command.occurredAt().isBefore(lastAccepted.value.plus(command.businessDedupWindow()))) {
            return new TimeboxResult(false, sum(buckets));
        }
        dedupKeys.put(dedupKey, new ExpirableInstant(command.occurredAt(), expiryNanos));

        long bucket = bucketStart(command);
        buckets.merge(bucket, 1, Integer::sum);
        int currentCount = sum(buckets);

        evictIfNeeded();

        return new TimeboxResult(currentCount >= command.threshold(), currentCount);
    }

    private boolean isProcessed(String key) {
        Long expiry = processedEvents.get(key);
        return expiry != null && expiry > System.nanoTime();
    }

    private void evictIfNeeded() {
        if (processedEvents.size() > EVICTION_THRESHOLD) {
            processedEvents.keySet().removeIf(k -> processedEvents.get(k) <= System.nanoTime());
        }
        if (dedupKeys.size() > EVICTION_THRESHOLD) {
            dedupKeys.keySet().removeIf(k -> dedupKeys.get(k).expired());
        }
    }

    private record ExpirableInstant(Instant value, long expiryNanos) {
        boolean notExpired() { return expiryNanos > System.nanoTime(); }
        boolean expired()    { return expiryNanos <= System.nanoTime(); }
    }

    private static long bucketStart(TimeboxCommand command) {
        long shardSeconds = command.shardSize().toSeconds();
        long occurredSeconds = command.occurredAt().getEpochSecond();
        return occurredSeconds - occurredSeconds % shardSeconds;
    }

    private static void removeExpiredBuckets(Map<Long, Integer> buckets, TimeboxCommand command) {
        long windowStart = command.occurredAt().minus(command.windowSize()).getEpochSecond();
        buckets.keySet().removeIf(bucket -> bucket < windowStart);
    }

    private static int sum(Map<Long, Integer> buckets) {
        return buckets.values().stream().mapToInt(Integer::intValue).sum();
    }

}
