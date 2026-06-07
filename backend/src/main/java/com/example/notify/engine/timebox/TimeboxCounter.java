package com.example.notify.engine.timebox;

import com.example.notify.domain.event.EventId;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class TimeboxCounter {

    private final Set<String> processedEvents = new HashSet<>();
    private final Map<String, Instant> dedupKeys = new HashMap<>();
    private final Map<String, Map<Long, Integer>> bucketsByKey = new HashMap<>();

    public synchronized TimeboxResult apply(TimeboxCommand command) {
        Map<Long, Integer> buckets = bucketsByKey.computeIfAbsent(command.windowKey(), ignored -> new HashMap<>());
        removeExpiredBuckets(buckets, command);
        String processedKey = command.windowKey() + ':' + command.eventId();
        if (processedEvents.contains(processedKey)) {
            return new TimeboxResult(false, sum(buckets));
        }
        processedEvents.add(processedKey);

        String dedupKey = command.dedupKey();
        Instant lastAccepted = dedupKeys.get(dedupKey);
        if (lastAccepted != null && command.occurredAt().isBefore(lastAccepted.plus(command.businessDedupWindow()))) {
            return new TimeboxResult(false, sum(buckets));
        }
        dedupKeys.put(dedupKey, command.occurredAt());

        long bucket = bucketStart(command);
        buckets.merge(bucket, 1, Integer::sum);
        int currentCount = sum(buckets);
        return new TimeboxResult(currentCount >= command.threshold(), currentCount);
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
