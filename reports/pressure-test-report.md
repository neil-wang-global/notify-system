# Notify System Pressure Test Report

Generated: 2026-06-08T20:07:38Z

## Environment

| Item | Value |
|---|---|
| Base URL | http://localhost:8080 |
| Strategy dedup window | 0s (disabled) |
| Strategy threshold | 3 (PRODUCT_VIEW), 2 (LOGIN) |
| Strategy window | 5m / 10s shard |

---

## Scenario 1: Baseline — Single User, Varying Request Count

Fixed: 1 user, 1 strategy (PRODUCT_VIEW), concurrency=10

### S1-total500

| Metric | Value |
|---|---|
| Total requests | 500 |
| Concurrency | 10 |
| Duration (ms) | 6817 |
| TPS (req/s) | 73 |
| Avg latency (ms) | 7.0 |
| P50 latency (ms) | 5.5 |
| P95 latency (ms) | 20.0 |
| P99 latency (ms) | 34.7 |
| Errors | 0 / 500 (0.00%) |

### S1-total1000

| Metric | Value |
|---|---|
| Total requests | 1000 |
| Concurrency | 10 |
| Duration (ms) | 12622 |
| TPS (req/s) | 79 |
| Avg latency (ms) | 5.8 |
| P50 latency (ms) | 4.7 |
| P95 latency (ms) | 12.8 |
| P99 latency (ms) | 24.7 |
| Errors | 0 / 1000 (0.00%) |

### S1-total2000

| Metric | Value |
|---|---|
| Total requests | 2000 |
| Concurrency | 10 |
| Duration (ms) | 24924 |
| TPS (req/s) | 80 |
| Avg latency (ms) | 6.0 |
| P50 latency (ms) | 4.9 |
| P95 latency (ms) | 13.1 |
| P99 latency (ms) | 26.8 |
| Errors | 0 / 2000 (0.00%) |

### S1-total5000

| Metric | Value |
|---|---|
| Total requests | 5000 |
| Concurrency | 10 |
| Duration (ms) | 57965 |
| TPS (req/s) | 86 |
| Avg latency (ms) | 5.2 |
| P50 latency (ms) | 4.4 |
| P95 latency (ms) | 10.1 |
| P99 latency (ms) | 19.2 |
| Errors | 0 / 5000 (0.00%) |

## Scenario 2: User Scale — Fixed 2000 Requests, Varying Users

Fixed: 2000 total requests, concurrency=20, 1 strategy (PRODUCT_VIEW)

### S2-users10

| Metric | Value |
|---|---|
| Total requests | 2000 |
| Concurrency | 20 |
| Duration (ms) | 22568 |
| TPS (req/s) | 89 |
| Avg latency (ms) | 12.2 |
| P50 latency (ms) | 9.7 |
| P95 latency (ms) | 28.2 |
| P99 latency (ms) | 48.5 |
| Errors | 0 / 2000 (0.00%) |

### S2-users50

| Metric | Value |
|---|---|
| Total requests | 2000 |
| Concurrency | 20 |
| Duration (ms) | 25551 |
| TPS (req/s) | 78 |
| Avg latency (ms) | 11.5 |
| P50 latency (ms) | 8.5 |
| P95 latency (ms) | 29.7 |
| P99 latency (ms) | 42.0 |
| Errors | 0 / 2000 (0.00%) |

### S2-users100

| Metric | Value |
|---|---|
| Total requests | 2000 |
| Concurrency | 20 |
| Duration (ms) | 24148 |
| TPS (req/s) | 83 |
| Avg latency (ms) | 13.3 |
| P50 latency (ms) | 10.1 |
| P95 latency (ms) | 35.4 |
| P99 latency (ms) | 48.9 |
| Errors | 0 / 2000 (0.00%) |

### S2-users300

| Metric | Value |
|---|---|
| Total requests | 2000 |
| Concurrency | 20 |
| Duration (ms) | 23061 |
| TPS (req/s) | 87 |
| Avg latency (ms) | 10.3 |
| P50 latency (ms) | 8.3 |
| P95 latency (ms) | 23.6 |
| P99 latency (ms) | 40.4 |
| Errors | 0 / 2000 (0.00%) |

## Scenario 3: Throughput — High Concurrency + Volume

Fixed: varying users, varying total, concurrency scaled

### S3-tp2000-c20

| Metric | Value |
|---|---|
| Total requests | 2000 |
| Concurrency | 20 |
| Duration (ms) | 22235 |
| TPS (req/s) | 90 |
| Avg latency (ms) | 10.2 |
| P50 latency (ms) | 8.4 |
| P95 latency (ms) | 23.3 |
| P99 latency (ms) | 39.2 |
| Errors | 0 / 2000 (0.00%) |

### S3-tp5000-c50

| Metric | Value |
|---|---|
| Total requests | 5000 |
| Concurrency | 50 |
| Duration (ms) | 58915 |
| TPS (req/s) | 85 |
| Avg latency (ms) | 15.6 |
| P50 latency (ms) | 10.7 |
| P95 latency (ms) | 45.0 |
| P99 latency (ms) | 84.5 |
| Errors | 0 / 5000 (0.00%) |

### S3-tp10000-c100

| Metric | Value |
|---|---|
| Total requests | 10000 |
| Concurrency | 100 |
| Duration (ms) | 116802 |
| TPS (req/s) | 86 |
| Avg latency (ms) | 16.6 |
| P50 latency (ms) | 11.2 |
| P95 latency (ms) | 48.8 |
| P99 latency (ms) | 87.6 |
| Errors | 0 / 10000 (0.00%) |

## Scenario 4: Mixed Event Types

Fixed: 2000 requests total, 50 users, concurrency=20, 2 strategies

### S4-mixed-view

| Metric | Value |
|---|---|
| Total requests | 1000 |
| Concurrency | 20 |
| Duration (ms) | 11762 |
| TPS (req/s) | 85 |
| Avg latency (ms) | 11.2 |
| P50 latency (ms) | 9.2 |
| P95 latency (ms) | 24.2 |
| P99 latency (ms) | 38.2 |
| Errors | 0 / 1000 (0.00%) |

### S4-mixed-login

| Metric | Value |
|---|---|
| Total requests | 1000 |
| Concurrency | 20 |
| Duration (ms) | 12555 |
| TPS (req/s) | 80 |
| Avg latency (ms) | 28.1 |
| P50 latency (ms) | 21.8 |
| P95 latency (ms) | 67.4 |
| P99 latency (ms) | 100.8 |
| Errors | 0 / 1000 (0.00%) |

## System State After Test

### Backend Status
```json
{"kafka":"RUNNING","redis":"HEALTHY","strategyCacheVersion":"available","degradationStatus":"NONE"}
```

### Notification Count
Notifications produced: 1002

### Docker Resource Usage
```
NAME                               CPU %     MEM USAGE / LIMIT
notify-system-backend-1            1.26%     1.24GiB / 5.772GiB
notify-system-postgres-replica-1   0.03%     40.42MiB / 5.772GiB
notify-system-kafka-1              1.49%     513.9MiB / 5.772GiB
notify-system-postgres-primary-1   0.01%     42.67MiB / 5.772GiB
notify-system-redis-node-1-1       1.30%     5.449MiB / 5.772GiB
notify-system-redis-node-2-1       1.33%     5.516MiB / 5.772GiB
notify-system-redis-node-3-1       1.38%     5.676MiB / 5.772GiB
notify-system-zookeeper-1          0.13%     106.1MiB / 5.772GiB
```

