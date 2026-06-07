# Notify System Pressure Test Report

## Measured Run

messagesSent=20000
durationMs=52
throughputMsgPerSecond=384615
mode=local

## Captured Metrics

- Input message TPS: 384615
- Consumer processing TPS: 384615 in local benchmark mode
- Notification publish TPS: not measured
- Notification persistence TPS: not measured
- P95/P99 latency: not measured
- Kafka lag: not measured
- Redis QPS/CPU/memory: not measured
- Error rate: 0 in local benchmark mode
- Backlog threshold TPS: not measured

## Docker / E2E Status

Docker daemon is available, but this environment does not provide `docker compose` or `docker-compose`. Because Compose cannot be invoked here, PostgreSQL primary/replica, Kafka, Redis, backend, and frontend were not started as a full Docker stack in this run.

This report must not be read as a Kafka + Redis + PostgreSQL pressure-test result. It is only a local benchmark of event generation and bucket counting.

## Verified Tests

- Backend unit test suite: passed.
- In-process E2E flow: passed. Simulated events matched a strategy, crossed the Timebox threshold, published a notification, and queried it through the notification API.
- Frontend build: passed earlier with `npm ci` and `npm run build --prefix frontend`.

## Storage Estimate

Worst case for 3,000,000 customers × 100 conditions × 24h window / 5m shards:

- Buckets per key: 288
- Counter bytes per bucket estimate: 16
- Worst active keys: 300,000,000
- Worst raw bucket bytes: 300,000,000 × 288 × 16 = 1,382,400,000,000 bytes, about 1.26 TiB before Redis object overhead.

Realistic storage is much lower because Timebox keys are created lazily only for active customers and matched candidate strategies. At 1% active customers and 10% active conditions, active keys are about 300,000 and raw bucket bytes are about 1.29 GiB before Redis overhead.

## Required Full Benchmark Command

Run this after installing Docker Compose support:

```bash
docker compose up -d postgres-primary postgres-replica redis zookeeper kafka kafka-topics backend frontend
MODE=http TOTAL=10000 BASE_URL=http://localhost:8080 ./scripts/benchmark-events.sh
```

The full report must then include Kafka lag, Redis QPS/CPU/memory, notification publish TPS, notification persistence TPS, P95/P99 latency, error rate, and backlog threshold TPS.
