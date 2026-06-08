#!/bin/bash
# Smoke test: verifies the Docker stack is healthy and backend APIs work end-to-end.
# Prerequisites: docker-compose up -d, backend running on localhost:8080 (or use --skip-backend).
#
# Usage: ./scripts/smoke-test.sh [--skip-backend]

set -euo pipefail

SKIP_BACKEND=false
[[ "${1:-}" == "--skip-backend" ]] && SKIP_BACKEND=true

PASS=0
FAIL=0
TOTAL=0

check() {
  local name="$1"
  shift
  TOTAL=$((TOTAL + 1))
  if bash -c "$*" >/dev/null 2>&1; then
    echo "  ✅ $name"
    PASS=$((PASS + 1))
  else
    echo "  ❌ $name"
    FAIL=$((FAIL + 1))
  fi
}

echo "=== Docker Stack Smoke Test ==="
echo ""

# ── Infrastructure ──
echo "Infrastructure:"

check "PostgreSQL primary accepts connections" \
  "docker exec notify-system-postgres-primary-1 pg_isready -U notify -d notify"

check "PostgreSQL replica is in recovery" \
  "docker exec notify-system-postgres-replica-1 psql -U notify -d notify -t -c 'SELECT pg_is_in_recovery()' | grep -q t"

check "PostgreSQL replication is streaming" \
  "docker exec notify-system-postgres-primary-1 psql -U notify -d notify -t -c 'SELECT state FROM pg_stat_replication' | grep -q streaming"

check "Redis cluster state is ok" \
  "docker exec notify-system-redis-node-1-1 redis-cli cluster info | grep -q 'cluster_state:ok'"

check "Redis cluster has 16384 slots assigned" \
  "docker exec notify-system-redis-node-1-1 redis-cli cluster info | grep -q 'cluster_slots_assigned:16384'"

check "Redis cluster has 3 master nodes" \
  "docker exec notify-system-redis-node-1-1 redis-cli cluster info | grep -q 'cluster_known_nodes:3'"

check "Kafka topics exist" \
  "docker exec notify-system-kafka-1 kafka-topics --bootstrap-server localhost:9092 --list | grep -q user-operation-events"

check "Kafka notification-events topic exists" \
  "docker exec notify-system-kafka-1 kafka-topics --bootstrap-server localhost:9092 --list | grep -q notification-events"

check "Kafka DLT topics exist" \
  "docker exec notify-system-kafka-1 kafka-topics --bootstrap-server localhost:9092 --list | grep -q user-operation-events-dlt && docker exec notify-system-kafka-1 kafka-topics --bootstrap-server localhost:9092 --list | grep -q notification-events-dlt"

echo ""

# ── PostgreSQL Data Layer (direct psql) ──
echo "PostgreSQL Data Layer:"

check "Strategies table exists" \
  "docker exec notify-system-postgres-primary-1 psql -U notify -d notify -c 'SELECT count(*) FROM strategies'"

check "Notification records table exists" \
  "docker exec notify-system-postgres-primary-1 psql -U notify -d notify -c 'SELECT count(*) FROM notification_records'"

check "Strategy scope IDs table exists" \
  "docker exec notify-system-postgres-primary-1 psql -U notify -d notify -c 'SELECT count(*) FROM strategy_scope_ids'"

check "Write to primary, read from replica" \
  "docker exec notify-system-postgres-primary-1 psql -U notify -d notify -c 'CREATE TABLE IF NOT EXISTS _smoke_test (id int PRIMARY KEY); INSERT INTO _smoke_test VALUES (1) ON CONFLICT DO NOTHING' && sleep 1 && docker exec notify-system-postgres-replica-1 psql -U notify -d notify -t -c 'SELECT id FROM _smoke_test' | grep -q 1"

echo ""

# ── Redis Cluster (direct redis-cli) ──
echo "Redis Cluster Data Layer:"

check "Set and get a key across cluster" \
  "docker exec notify-system-redis-node-1-1 redis-cli -c SET smoke:test hello && docker exec notify-system-redis-node-1-1 redis-cli -c GET smoke:test | grep -q hello"

check "Cleanup smoke key" \
  "docker exec notify-system-redis-node-1-1 redis-cli -c DEL smoke:test"

echo ""

# ── Backend APIs ──
if [[ "$SKIP_BACKEND" == true ]]; then
  echo "Backend APIs: (skipped)"
else
  echo "Backend APIs:"

  API_BASE="http://localhost:8080"

  check "GET /api/status returns 200" \
    "curl -sf '$API_BASE/api/status' | grep -q healthy"

  check "POST /api/strategies returns 200" \
    "curl -sf -X POST '$API_BASE/api/strategies' -H 'Content-Type: application/json' -d '{\"strategyId\":\"smoke-strategy-1\",\"name\":\"Smoke Test\",\"scope\":{\"kind\":\"GLOBAL\",\"userIds\":[],\"userGroupIds\":[]},\"eventType\":\"PRODUCT_VIEW\",\"executionPlan\":\"PT30S:PT10S:PT0S:customerId,userId,eventType\",\"expectedVersion\":0,\"userToken\":\"smoke-user\",\"idempotencyKey\":\"smoke-idem-1\"}' | grep -q smoke-strategy-1"

  check "POST /api/events/simulate returns 200" \
    "curl -sf -X POST '$API_BASE/api/events/simulate' -H 'Content-Type: application/json' -d '{\"customerId\":\"C001\",\"userId\":\"U001\",\"userGroupIds\":[],\"eventType\":\"PRODUCT_VIEW\",\"productId\":\"P001\",\"channel\":\"APP\"}' | grep -q productId"

  check "GET /api/notifications returns 200" \
    "curl -sf '$API_BASE/api/notifications' | grep -q notification"

  check "GET /api/exceptions/user-operations returns 200" \
    "curl -sf '$API_BASE/api/exceptions/user-operations'"

  check "GET /api/exceptions/notifications returns 200" \
    "curl -sf '$API_BASE/api/exceptions/notifications'"
fi

echo ""

# ── Frontend ──
echo "Frontend:"

if [[ "$SKIP_BACKEND" == true ]]; then
  echo "  (skipped — requires backend)"
else
  check "Frontend returns 200" \
    "curl -sf -o /dev/null -w '%{http_code}' 'http://localhost:5173/' | grep -q 200"
fi

echo ""
echo "=== Results: $PASS/$TOTAL passed, $FAIL failed ==="

if [[ $FAIL -gt 0 ]]; then
  exit 1
fi
