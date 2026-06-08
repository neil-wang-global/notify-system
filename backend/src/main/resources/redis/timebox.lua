-- KEYS[1] = processed:{eventId}
-- KEYS[2] = dedup:{strategyId}:{dedupHash}
-- KEYS[3] = timebox:{strategyId}:{customerId}:{dedupHash}
-- ARGV[1] = eventId
-- ARGV[2] = dedupWindowMs (0 = disabled)
-- ARGV[3] = currentBucketTimestamp (ms)
-- ARGV[4] = windowSizeMs
-- ARGV[5] = shardSizeMs
-- ARGV[6] = threshold
-- ARGV[7] = ttlMs (windowSizeMs + shardSizeMs * 2)
-- ARGV[8] = wallClockMs (current wall-clock time in ms)
-- Returns: {triggered(0|1), currentCount, wasDuplicate(0|1)}

-- Event idempotency check
if redis.call('EXISTS', KEYS[1]) == 1 then
    return {0, 0, 1}
end
redis.call('SET', KEYS[1], '1', 'PX', ARGV[7])

-- Business dedup check
local dedupWindowMs = tonumber(ARGV[2])
if dedupWindowMs > 0 then
    if redis.call('EXISTS', KEYS[2]) == 1 then
        return {0, 0, 1}
    end
    redis.call('SET', KEYS[2], '1', 'PX', dedupWindowMs)
end

-- Update current bucket
local currentBucket = ARGV[3]
redis.call('HINCRBY', KEYS[3], currentBucket, 1)

-- Compute window sum using wall-clock time (T-18) and clean old buckets (T-17)
local shardMs = tonumber(ARGV[5])
local windowMs = tonumber(ARGV[4])
local nowMs = tonumber(ARGV[8])
local numBuckets = math.ceil(windowMs / shardMs)
local sum = 0
local windowStart = nowMs - windowMs
for i = 0, numBuckets - 1 do
    local bucketTs = tostring(nowMs - i * shardMs)
    local count = tonumber(redis.call('HGET', KEYS[3], bucketTs) or '0')
    sum = sum + count
    -- T-17: delete buckets that fall outside the window
    if tonumber(bucketTs) < windowStart then
        redis.call('HDEL', KEYS[3], bucketTs)
    end
end

-- Set TTL
redis.call('PEXPIRE', KEYS[3], ARGV[7])

-- Threshold check
local threshold = tonumber(ARGV[6])
local triggered = 0
if sum >= threshold then
    triggered = 1
end

return {triggered, sum, 0}
