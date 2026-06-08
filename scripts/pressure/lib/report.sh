#!/usr/bin/env sh
# Report generation from raw TSV data.
# Input: file with lines of "http_code<TAB>latency_seconds"
# Usage: generate_report <tsv_file> <scenario_name> <total> <concurrency> <duration_ms>
set -eu

TSV_FILE="${1:?tsv file required}"
SCENARIO="${2:-scenario}"
TOTAL="${3:-0}"
CONCURRENCY="${4:-1}"
DURATION_MS="${5:-0}"

if [ ! -s "$TSV_FILE" ]; then
  echo "### $SCENARIO"
  echo ""
  echo "**No data collected**"
  echo ""
  return 0 2>/dev/null || exit 0
fi

# Count errors (non-2xx)
total_lines=$(wc -l < "$TSV_FILE" | tr -d ' ')
errors=$(awk -F'\t' '$1 < 200 || $1 >= 300' "$TSV_FILE" | wc -l | tr -d ' ')
if [ "$total_lines" -eq 0 ]; then total_lines=1; fi
error_rate=$(awk "BEGIN { printf \"%.2f\", ($errors / $total_lines) * 100 }")

# Latency stats (in ms)
p50=$(awk -F'\t' '{print $2 * 1000}' "$TSV_FILE" | sort -n | awk '{a[NR]=$1} END {if(NR==0) print 0; else print a[int(NR*0.50)+1]}' | awk '{printf "%.1f", $1}')
p95=$(awk -F'\t' '{print $2 * 1000}' "$TSV_FILE" | sort -n | awk '{a[NR]=$1} END {if(NR==0) print 0; else print a[int(NR*0.95)+1]}' | awk '{printf "%.1f", $1}')
p99=$(awk -F'\t' '{print $2 * 1000}' "$TSV_FILE" | sort -n | awk '{a[NR]=$1} END {if(NR==0) print 0; else print a[int(NR*0.99)+1]}' | awk '{printf "%.1f", $1}')
avg=$(awk -F'\t' '{sum+=$2*1000; n++} END {if(n==0) print 0; else printf "%.1f", sum/n}' "$TSV_FILE")

# TPS
if [ "$DURATION_MS" -le 0 ]; then DURATION_MS=1; fi
tps=$(awk "BEGIN { printf \"%.0f\", ($total_lines / $DURATION_MS) * 1000 }")

echo "### $SCENARIO"
echo ""
echo "| Metric | Value |"
echo "|---|---|"
echo "| Total requests | $total_lines |"
echo "| Concurrency | $CONCURRENCY |"
echo "| Duration (ms) | $DURATION_MS |"
echo "| TPS (req/s) | $tps |"
echo "| Avg latency (ms) | $avg |"
echo "| P50 latency (ms) | $p50 |"
echo "| P95 latency (ms) | $p95 |"
echo "| P99 latency (ms) | $p99 |"
echo "| Errors | $errors / $total_lines ($error_rate%) |"
echo ""
