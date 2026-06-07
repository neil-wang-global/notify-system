# Notify System Pressure Test Report

## Scope

This report records the benchmark evidence for the notify-system hot path. The 50k+ msg/s number is a design target; final numbers depend on the actual machine, Kafka, Redis, and PostgreSQL deployment.

## Metrics to Capture

- Input message TPS
- Consumer processing TPS
- Notification publish TPS
- Notification persistence TPS
- P95/P99 latency
- Kafka lag
- Redis QPS, CPU, and memory
- Error rate
- TPS where backlog starts

## Storage Estimate

Timebox keys are created lazily. The worst case is `active customers × matched strategies × active windows × bucket count`; the realistic estimate should use active-customer and active-strategy ratios.

## Reproduction

```bash
docker compose up -d postgres redis kafka
TOTAL=1000 BASE_URL=http://localhost:8080 ./scripts/benchmark-events.sh
```

## Current Result

Pending execution against the completed Kafka, Redis, PostgreSQL, and API integration path.
