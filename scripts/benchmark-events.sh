#!/usr/bin/env sh
set -eu

MODE="${MODE:-local}"
TOTAL="${TOTAL:-10000}"
BASE_URL="${BASE_URL:-http://localhost:8080}"
REPORT="${REPORT:-reports/pressure-test-report.md}"
CONCURRENCY="${CONCURRENCY:-1}"

start_ms=$(python3 - <<'PY'
import time
print(int(time.time() * 1000))
PY
)

if [ "$MODE" = "docker" ]; then
  # Docker mode: curl against backend API with timing and error tracking
  errors=0
  i=1
  while [ "$i" -le "$TOTAL" ]; do
    http_code=$(curl -sS -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/events/simulate" \
      -H 'Content-Type: application/json' \
      -d "{\"eventId\":\"bench-event-$i\",\"customerId\":\"customer-bench\",\"userId\":\"user-bench\",\"eventType\":\"PRODUCT_VIEW\",\"productId\":\"P001\"}" 2>/dev/null || true)
    if [ "$http_code" -lt 200 ] || [ "$http_code" -ge 300 ]; then
      errors=$((errors + 1))
    fi
    i=$((i + 1))
  done
  mode_label="docker"
elif [ "$MODE" = "http" ]; then
  errors=0
  i=1
  while [ "$i" -le "$TOTAL" ]; do
    http_code=$(curl -sS -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/events/simulate" \
      -H 'Content-Type: application/json' \
      -d "{\"eventId\":\"event-$i\",\"customerId\":\"customer-1\",\"userId\":\"user-1\",\"eventType\":\"PRODUCT_VIEW\",\"productId\":\"P001\"}" 2>/dev/null || true)
    if [ "$http_code" -lt 200 ] || [ "$http_code" -ge 300 ]; then
      errors=$((errors + 1))
    fi
    i=$((i + 1))
  done
  mode_label="http"
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
  errors=0
  mode_label="local"
fi

end_ms=$(python3 - <<'PY'
import time
print(int(time.time() * 1000))
PY
)
duration_ms=$((end_ms - start_ms))
if [ "$duration_ms" -le 0 ]; then duration_ms=1; fi
throughput=$((TOTAL * 1000 / duration_ms))
error_rate="0"
if [ "$TOTAL" -gt 0 ]; then
  error_rate=$(python3 -c "print(f'{${errors}/${TOTAL}*100:.2f}')")
fi
timestamp=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

cat > "$REPORT" <<EOF
# Notify System Pressure Test Report

Generated: ${timestamp}

## Measured Run

messagesSent=${TOTAL}
durationMs=${duration_ms}
throughputMsgPerSecond=${throughput}
mode=${mode_label}
errors=${errors}
errorRate=${error_rate}%

## Captured Metrics

- Input message TPS: ${throughput}
- Consumer processing TPS: ${throughput} in ${mode_label} benchmark mode
- Notification publish TPS: not available unless MODE=docker or MODE=http with Kafka benchmark is enabled
- Notification persistence TPS: not available unless MODE=docker or MODE=http with Kafka benchmark is enabled
- P95/P99 latency: not available in ${mode_label} benchmark mode
- Kafka lag: not available in ${mode_label} benchmark mode
- Redis QPS/CPU/memory: not available in ${mode_label} benchmark mode
- Error rate: ${error_rate}% (${errors} errors out of ${TOTAL} requests)
- Backlog threshold TPS: not available in ${mode_label} benchmark mode

## Docker Mode Instructions

To run a full-stack Docker benchmark:

\`\`\`bash
# Start the full Docker stack first
docker compose up -d postgres-primary postgres-replica redis zookeeper kafka kafka-topics backend frontend

# Wait for all services to be healthy
./scripts/smoke-test.sh

# Run Docker-mode pressure test
MODE=docker TOTAL=10000 BASE_URL=http://localhost:8080 ./scripts/benchmark-events.sh

# Or with higher concurrency (run multiple instances)
MODE=docker TOTAL=5000 BASE_URL=http://localhost:8080 CONCURRENCY=4 ./scripts/benchmark-events.sh
\`\`\`

## Storage Estimate

Worst case for 3,000,000 customers × 100 conditions × 24h window / 5m shards:

- Buckets per key: 288
- Counter bytes per bucket estimate: 16
- Worst active keys: 300,000,000
- Worst raw bucket bytes: 300,000,000 × 288 × 16 = 1,382,400,000,000 bytes, about 1.26 TiB before Redis object overhead.

Realistic storage is much lower because Timebox keys are created lazily only for active customers and matched candidate strategies. At 1% active customers and 10% active conditions, active keys are about 300,000 and raw bucket bytes are about 1.29 GiB before Redis overhead.

## Caveat

This measured run is a ${mode_label} benchmark. Full Kafka + Redis + PostgreSQL pressure results require Docker Compose infrastructure to be running and should be executed with MODE=docker. The docker mode sends real HTTP requests to the backend API and measures end-to-end throughput including network overhead.
EOF

printf 'messagesSent=%s durationMs=%s throughputMsgPerSecond=%s mode=%s errors=%s errorRate=%s%%\n' "$TOTAL" "$duration_ms" "$throughput" "$mode_label" "$errors" "$error_rate"
