#!/usr/bin/env sh
# Core HTTP benchmarker: sends N requests with P concurrent workers.
# Usage: http_bench <base_url> <total> <concurrency> <payload_template>
# Outputs TSV lines to stdout: http_code<TAB>latency_ms
set -eu

BASE_URL="${1:?base_url required}"
TOTAL="${2:?total required}"
CONCURRENCY="${3:-1}"
PAYLOAD_TEMPLATE="${4:-}"

# Write a temp file with all payloads so workers can pick their line
TMPDIR=$(mktemp -d)
trap 'rm -rf "$TMPDIR"' EXIT

i=1
while [ "$i" -le "$TOTAL" ]; do
  # Replace __ID__, __USER__, __CUST__ placeholders
  payload=$(echo "$PAYLOAD_TEMPLATE" | sed "s/__ID__/$i/g")
  echo "$payload" > "$TMPDIR/req-$i.json"
  i=$((i + 1))
done

# Worker: sends one request, prints "http_code<TAB>latency_ms"
send_one() {
  req_file="$1"
  base_url="$2"
  result=$(curl -sS -o /dev/null -w "%{http_code}\t%{time_total}" \
    -X POST "$base_url/api/events/simulate" \
    -H 'Content-Type: application/json' \
    -d @"$req_file" 2>/dev/null || echo "000	0.000")
  echo "$result"
}
export -f send_one
export BASE_URL

# Fan out with xargs -P
printf '%s\n' "$TMPDIR"/req-*.json | sort -t'-' -k2 -n | \
  xargs -P "$CONCURRENCY" -I{} sh -c 'send_one "$1" "$2"' _ {} "$BASE_URL"
