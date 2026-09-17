#!/usr/bin/env bash
# Fires N concurrent requests for the SAME product id at either the naive
# or the lock-protected endpoint and prints how many times the backing
# store was actually called (via /api/stats).
#
# Usage:
#   ./scripts/load-test.sh naive 20     # 20 concurrent requests, no protection
#   ./scripts/load-test.sh safe  20     # 20 concurrent requests, distributed lock
#
set -euo pipefail

MODE="${1:-safe}"        # naive | safe
CONCURRENCY="${2:-20}"
BASE_URL="${BASE_URL:-http://localhost:8080}"
PRODUCT_ID="demo-1"

if [[ "$MODE" != "naive" && "$MODE" != "safe" ]]; then
  echo "First argument must be 'naive' or 'safe'" >&2
  exit 1
fi

echo "Resetting caches and counters..."
curl -s -X POST "$BASE_URL/api/reset" > /dev/null

echo "Firing $CONCURRENCY concurrent requests at /api/$MODE/$PRODUCT_ID ..."
start=$(date +%s.%N)

for i in $(seq 1 "$CONCURRENCY"); do
  curl -s -o /dev/null "$BASE_URL/api/$MODE/$PRODUCT_ID" &
done
wait

end=$(date +%s.%N)
elapsed=$(echo "$end - $start" | bc)

echo ""
echo "Done in ${elapsed}s"
echo ""
echo "Stats (call /api/stats yourself for live numbers):"
curl -s "$BASE_URL/api/stats" | python3 -m json.tool

echo ""
echo "Expected result:"
echo "  - mode=naive: backingStoreCalls will be close to $CONCURRENCY (a stampede)."
echo "  - mode=safe:  backingStoreCalls will be 1 (only the lock holder recomputed;"
echo "                everyone else either got a cache hit or waited for it)."
