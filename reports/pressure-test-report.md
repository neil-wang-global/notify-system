# Notify System Pressure Test Report

Generated: 2026-06-08T12:51:50Z

## Measured Run

messagesSent=20000
durationMs=64
throughputMsgPerSecond=312500
mode=local
errors=0
errorRate=0.00%

## Captured Metrics

- Input message TPS: 312500
- Consumer processing TPS: 312500 in local benchmark mode
- Notification publish TPS: not available unless MODE=docker or MODE=http with Kafka benchmark is enabled
- Notification persistence TPS: not available unless MODE=docker or MODE=http with Kafka benchmark is enabled
- P95/P99 latency: not available in local benchmark mode
- Kafka lag: not available in local benchmark mode
- Redis QPS/CPU/memory: not available in local benchmark mode
- Error rate: 0.00% (0 errors out of 20000 requests)
- Backlog threshold TPS: not available in local benchmark mode

## Docker Mode Instructions

To run a full-stack Docker benchmark:

```bash
# Start the full Docker stack first
docker compose up -d postgres-primary postgres-replica redis zookeeper kafka kafka-topics backend frontend

# Wait for all services to be healthy
./scripts/smoke-test.sh

# Run Docker-mode pressure test
MODE=docker TOTAL=10000 BASE_URL=http://localhost:8080 ./scripts/benchmark-events.sh

# Or with higher concurrency (run multiple instances)
MODE=docker TOTAL=5000 BASE_URL=http://localhost:8080 CONCURRENCY=4 ./scripts/benchmark-events.sh
```

### Docker Mode Results

_Results will be populated when the benchmark is run against the full Docker stack._

| Metric | Placeholder |
|---|---|
| Total events sent | _run with MODE=docker_ |
| Duration (ms) | _run with MODE=docker_ |
| TPS (events/sec) | _run with MODE=docker_ |
| Error rate | _run with MODE=docker_ |
| Kafka lag | _run with MODE=docker_ |
| Redis QPS | _run with MODE=docker_ |
| PG notification TPS | _run with MODE=docker_ |
| P95 latency | _run with MODE=docker_ |
| P99 latency | _run with MODE=docker_ |

## Storage Estimate

Worst case for 3,000,000 customers × 100 conditions × 24h window / 5m shards:

- Buckets per key: 288
- Counter bytes per bucket estimate: 16
- Worst active keys: 300,000,000
- Worst raw bucket bytes: 300,000,000 × 288 × 16 = 1,382,400,000,000 bytes, about 1.26 TiB before Redis object overhead.

Realistic storage is much lower because Timebox keys are created lazily only for active customers and matched candidate strategies. At 1% active customers and 10% active conditions, active keys are about 300,000 and raw bucket bytes are about 1.29 GiB before Redis overhead.

## Caveat

This measured run is a local benchmark. Full Kafka + Redis + PostgreSQL pressure results require Docker Compose infrastructure to be running and should be executed with MODE=docker. The docker mode sends real HTTP requests to the backend API and measures end-to-end throughput including network overhead.
