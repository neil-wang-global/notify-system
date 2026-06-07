#!/usr/bin/env sh
set -eu

BASE_URL="${BASE_URL:-http://localhost:8080}"
TOTAL="${TOTAL:-1000}"

start=$(date +%s)
i=1
while [ "$i" -le "$TOTAL" ]; do
  curl -sS -o /dev/null -X POST "$BASE_URL/events/simulate" \
    -H 'Content-Type: application/json' \
    -d "{\"eventId\":\"event-$i\",\"customerId\":\"customer-1\",\"userId\":\"user-1\",\"eventType\":\"PRODUCT_VIEW\",\"productId\":\"P001\"}"
  i=$((i + 1))
done
end=$(date +%s)
elapsed=$((end - start))
if [ "$elapsed" -eq 0 ]; then elapsed=1; fi
printf 'sent=%s elapsed_seconds=%s approx_tps=%s\n' "$TOTAL" "$elapsed" "$((TOTAL / elapsed))"
