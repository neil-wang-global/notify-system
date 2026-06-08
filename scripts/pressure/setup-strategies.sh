#!/usr/bin/env sh
# Create test strategies with dedupWindow=0s for pressure testing.
# Usage: setup-strategies.sh [base_url]
set -eu

BASE_URL="${1:-http://localhost:8080}"

echo "=== Setting up pressure test strategies ==="

# Strategy 1: PRODUCT_VIEW, threshold=3, no rules, dedup=0s
curl -sS -X POST "$BASE_URL/api/strategies" \
  -H 'Content-Type: application/json' \
  -d '{
    "strategyId": "perf-s1",
    "name": "Performance Test - PRODUCT_VIEW",
    "eventType": "PRODUCT_VIEW",
    "threshold": 3,
    "windowSize": "5m",
    "businessDedupWindowSeconds": 0,
    "userToken": "perf-test-user",
    "idempotencyKey": "perf-s1-key",
    "scope": {"kind": "GLOBAL"},
    "rules": []
  }' && echo " -> perf-s1 created"

# Strategy 2: LOGIN, threshold=2, no rules, dedup=0s
curl -sS -X POST "$BASE_URL/api/strategies" \
  -H 'Content-Type: application/json' \
  -d '{
    "strategyId": "perf-s2",
    "name": "Performance Test - LOGIN",
    "eventType": "LOGIN",
    "threshold": 2,
    "windowSize": "5m",
    "businessDedupWindowSeconds": 0,
    "userToken": "perf-test-user",
    "idempotencyKey": "perf-s2-key",
    "scope": {"kind": "GLOBAL"},
    "rules": []
  }' && echo " -> perf-s2 created"

echo "=== Strategies ready ==="
