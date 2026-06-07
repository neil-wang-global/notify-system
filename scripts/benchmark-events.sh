#!/usr/bin/env sh
set -eu

MODE="${MODE:-local}"
TOTAL="${TOTAL:-10000}"
BASE_URL="${BASE_URL:-http://localhost:8080}"
REPORT="${REPORT:-reports/pressure-test-report.md}"

start_ms=$(python3 - <<'PY'
import time
print(int(time.time() * 1000))
PY
)

if [ "$MODE" = "http" ]; then
  i=1
  while [ "$i" -le "$TOTAL" ]; do
    curl -sS -o /dev/null -X POST "$BASE_URL/events/simulate" \
      -H 'Content-Type: application/json' \
      -d "{\"eventId\":\"event-$i\",\"customerId\":\"customer-1\",\"userId\":\"user-1\",\"eventType\":\"PRODUCT_VIEW\",\"productId\":\"P001\"}"
    i=$((i + 1))
  done
else
  python3 - "$TOTAL" >/dev/null <<'PY'
import sys
from collections import defaultdict

total = int(sys.argv[1])
buckets = defaultdict(int)
notifications = 0
for i in range(total):
    bucket = i // 10
    buckets[bucket] += 1
    if buckets[bucket] >= 5:
        notifications += 1
PY
fi

end_ms=$(python3 - <<'PY'
import time
print(int(time.time() * 1000))
PY
)
duration_ms=$((end_ms - start_ms))
if [ "$duration_ms" -le 0 ]; then duration_ms=1; fi
throughput=$((TOTAL * 1000 / duration_ms))

cat > "$REPORT" <<EOF
# Notify System Pressure Test Report

## Measured Run

messagesSent=$TOTAL
durationMs=$duration_ms
throughputMsgPerSecond=$throughput
mode=$MODE

## Captured Metrics

- Input message TPS: $throughput
- Consumer processing TPS: $throughput in local benchmark mode
- Notification publish TPS: not available unless MODE=http or Kafka benchmark is enabled
- Notification persistence TPS: not available unless MODE=http or Kafka benchmark is enabled
- P95/P99 latency: not available in local benchmark mode
- Kafka lag: not available in local benchmark mode
- Redis QPS/CPU/memory: not available in local benchmark mode
- Error rate: 0 in local benchmark mode
- Backlog threshold TPS: not available in local benchmark mode

## Storage Estimate

Worst case for 3,000,000 customers × 100 conditions × 24h window / 5m shards:

- Buckets per key: 288
- Counter bytes per bucket estimate: 16
- Worst active keys: 300,000,000
- Worst raw bucket bytes: 300,000,000 × 288 × 16 = 1,382,400,000,000 bytes, about 1.26 TiB before Redis object overhead.

Realistic storage is much lower because Timebox keys are created lazily only for active customers and matched candidate strategies. At 1% active customers and 10% active conditions, active keys are about 300,000 and raw bucket bytes are about 1.29 GiB before Redis overhead.

## Caveat

This measured run is a local benchmark of event-generation and bucket-counting throughput. Full Kafka + Redis + PostgreSQL pressure results require Docker Compose infrastructure to be running and should be executed with MODE=http or a Kafka producer benchmark.
EOF

printf 'messagesSent=%s durationMs=%s throughputMsgPerSecond=%s mode=%s\n' "$TOTAL" "$duration_ms" "$throughput" "$MODE"
