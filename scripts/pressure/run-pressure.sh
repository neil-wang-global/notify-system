#!/usr/bin/env sh
# Main pressure test runner.
# Usage: ./run-pressure.sh [base_url]
set -eu

BASE_URL="${1:-http://localhost:8080}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TMPDIR=$(mktemp -d)
trap 'rm -rf "$TMPDIR"' EXIT

REPORT_FILE="docs/reports/pressure-test-report-$(date +%Y%m%d-%H%M%S).md"

echo "=== Notify System Pressure Test ==="
echo "Base URL: $BASE_URL"
echo "Report:   $REPORT_FILE"
echo ""

# --- Phase 0: Setup ---
echo "--- Phase 0: Creating strategies (dedup=0s) ---"
sh "$SCRIPT_DIR/setup-strategies.sh" "$BASE_URL"
echo ""

# Wait for Redis refresh
sleep 2

# --- Helper: run one bench scenario ---
run_scenario() {
  local name="$1"
  local total="$2"
  local concurrency="$3"
  local payload="$4"
  local tsv="$TMPDIR/${name}.tsv"

  echo "--- Running: $name (total=$total, concurrency=$concurrency) ---" >&2

  start_ms=$(python3 -c 'import time; print(int(time.time()*1000))')

  # Generate payload files
  i=1
  while [ "$i" -le "$total" ]; do
    p=$(echo "$payload" | sed "s/__ID__/$i/g")
    echo "$p" > "$TMPDIR/${name}-$i.json"
    i=$((i + 1))
  done

  # Send concurrently, output TSV: http_code<TAB>latency_seconds
  printf '%s\n' "$TMPDIR/${name}"-*.json | sort -t'-' -k2 -n | \
    xargs -P "$concurrency" -I{} sh -c \
    'curl -sS -o /dev/null -w "%{http_code}\t%{time_total}\n" -X POST "$0/api/events/simulate" -H "Content-Type: application/json" -d @"$1" 2>/dev/null || echo "000	0.000"' \
    "$BASE_URL" {} > "$tsv" 2>/dev/null

  end_ms=$(python3 -c 'import time; print(int(time.time()*1000))')
  duration_ms=$((end_ms - start_ms))

  # Generate report section
  local total_lines=$(wc -l < "$tsv" | tr -d ' ')
  local errors=$(awk -F'\t' '$1 < 200 || $1 >= 300' "$tsv" | wc -l | tr -d ' ')
  [ "$total_lines" -eq 0 ] && total_lines=1
  local error_rate=$(awk "BEGIN { printf \"%.2f\", ($errors / $total_lines) * 100 }")

  local p50=$(awk -F'\t' '{print $2 * 1000}' "$tsv" | sort -n | awk '{a[NR]=$1} END {if(NR==0) print 0; else print a[int(NR*0.50)+1]}' | awk '{printf "%.1f", $1}')
  local p95=$(awk -F'\t' '{print $2 * 1000}' "$tsv" | sort -n | awk '{a[NR]=$1} END {if(NR==0) print 0; else print a[int(NR*0.95)+1]}' | awk '{printf "%.1f", $1}')
  local p99=$(awk -F'\t' '{print $2 * 1000}' "$tsv" | sort -n | awk '{a[NR]=$1} END {if(NR==0) print 0; else print a[int(NR*0.99)+1]}' | awk '{printf "%.1f", $1}')
  local avg=$(awk -F'\t' '{sum+=$2*1000; n++} END {if(n==0) print 0; else printf "%.1f", sum/n}' "$tsv")

  [ "$duration_ms" -le 0 ] && duration_ms=1
  local tps=$(awk "BEGIN { printf \"%.0f\", ($total_lines / $duration_ms) * 1000 }")

  echo "### $name"
  echo ""
  echo "| Metric | Value |"
  echo "|---|---|"
  echo "| Total requests | $total_lines |"
  echo "| Concurrency | $concurrency |"
  echo "| Duration (ms) | $duration_ms |"
  echo "| TPS (req/s) | $tps |"
  echo "| Avg latency (ms) | $avg |"
  echo "| P50 latency (ms) | $p50 |"
  echo "| P95 latency (ms) | $p95 |"
  echo "| P99 latency (ms) | $p99 |"
  echo "| Errors | $errors / $total_lines ($error_rate%) |"
  echo ""
}

# ======================================================================
# SCENARIO 1: Baseline — single user, varying request counts
# ======================================================================
echo "=== Scenario 1: Baseline ===" >&2
{
  echo "## Scenario 1: Baseline — Single User, Varying Request Count"
  echo ""
  echo "Fixed: 1 user, 1 strategy (PRODUCT_VIEW), concurrency=10"
  echo ""

  for total in 500 1000 2000 5000; do
    PAYLOAD='{"eventId":"s1-__ID__","customerId":"c-baseline","userId":"u-baseline","eventType":"PRODUCT_VIEW","fields":{"productId":"P001"}}'
    run_scenario "S1-total${total}" "$total" 10 "$PAYLOAD"
  done
} >> "$TMPDIR/report-body.md"

# ======================================================================
# SCENARIO 2: User scale — fixed 2000 requests, varying user count
# ======================================================================
echo "=== Scenario 2: User Scale ===" >&2
{
  echo "## Scenario 2: User Scale — Fixed 2000 Requests, Varying Users"
  echo ""
  echo "Fixed: 2000 total requests, concurrency=20, 1 strategy (PRODUCT_VIEW)"
  echo ""

  for users in 10 50 100 300; do
    PAYLOAD='{"eventId":"s2-__ID__","customerId":"c-u'"$users"'","userId":"u-__ID__","eventType":"PRODUCT_VIEW","fields":{"productId":"P001"}}'
    run_scenario "S2-users${users}" 2000 20 "$PAYLOAD"
  done
} >> "$TMPDIR/report-body.md"

# ======================================================================
# SCENARIO 3: Throughput — high concurrency, high volume
# ======================================================================
echo "=== Scenario 3: Throughput ===" >&2
{
  echo "## Scenario 3: Throughput — High Concurrency + Volume"
  echo ""
  echo "Fixed: varying users, varying total, concurrency scaled"
  echo ""

  for total in 2000 5000 10000; do
    conc=$((total / 100))
    [ "$conc" -lt 10 ] && conc=10
    [ "$conc" -gt 100 ] && conc=100
    PAYLOAD='{"eventId":"s3-__ID__","customerId":"c-tp","userId":"u-__ID__","eventType":"PRODUCT_VIEW","fields":{"productId":"P001"}}'
    run_scenario "S3-tp${total}-c${conc}" "$total" "$conc" "$PAYLOAD"
  done
} >> "$TMPDIR/report-body.md"

# ======================================================================
# SCENARIO 4: Mixed event types
# ======================================================================
echo "=== Scenario 4: Mixed Event Types ===" >&2
{
  echo "## Scenario 4: Mixed Event Types"
  echo ""
  echo "Fixed: 2000 requests total, 50 users, concurrency=20, 2 strategies"
  echo ""

  PAYLOAD='{"eventId":"s4v-__ID__","customerId":"c-mixed","userId":"u-__ID__","eventType":"PRODUCT_VIEW","fields":{"productId":"P001"}}'
  run_scenario "S4-mixed-view" 1000 20 "$PAYLOAD"

  PAYLOAD='{"eventId":"s4l-__ID__","customerId":"c-mixed","userId":"u-__ID__","eventType":"LOGIN","fields":{}}'
  run_scenario "S4-mixed-login" 1000 20 "$PAYLOAD"
} >> "$TMPDIR/report-body.md"

# ======================================================================
# Collect system metrics
# ======================================================================
echo "=== Collecting system metrics ===" >&2
{
  echo "## System State After Test"
  echo ""

  echo "### Backend Status"
  echo '```json'
  curl -sS "$BASE_URL/api/status" 2>/dev/null || echo '{"error": "unreachable"}'
  echo ''
  echo '```'
  echo ""

  echo "### Notification Count"
  notif_count=$(curl -sS "$BASE_URL/api/notifications?limit=10000" 2>/dev/null | python3 -c 'import sys,json; d=json.load(sys.stdin); print(len(d))' 2>/dev/null || echo "N/A")
  echo "Notifications produced: $notif_count"
  echo ""

  echo "### Docker Resource Usage"
  echo '```'
  docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}" 2>/dev/null || echo "Docker stats unavailable"
  echo '```'
  echo ""
} >> "$TMPDIR/report-body.md"

# ======================================================================
# Assemble final report
# ======================================================================
timestamp=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

{
  echo "# Notify System Pressure Test Report"
  echo ""
  echo "Generated: $timestamp"
  echo ""
  echo "## Environment"
  echo ""
  echo "| Item | Value |"
  echo "|---|---|"
  echo "| Base URL | $BASE_URL |"
  echo "| Strategy dedup window | 0s (disabled) |"
  echo "| Strategy threshold | 3 (PRODUCT_VIEW), 2 (LOGIN) |"
  echo "| Strategy window | 5m / 10s shard |"
  echo ""
  echo "---"
  echo ""
  cat "$TMPDIR/report-body.md"
} > "$REPORT_FILE"

cp "$REPORT_FILE" "docs/reports/pressure-test-report.md"

echo ""
echo "=== Report saved to $REPORT_FILE ==="
echo "=== Also saved to docs/reports/pressure-test-report.md ==="
