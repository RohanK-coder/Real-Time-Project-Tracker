#!/usr/bin/env bash
# Runs the k6 load test inside the deploy/ stack's Docker network and samples per-replica
# server metrics while it runs. Results land in loadtest/results/.
set -euo pipefail
cd "$(dirname "$0")/.."

NETWORK=${NETWORK:-tracker_default}
RUN_ID=${RUN_ID:-$(date +%Y%m%d-%H%M%S)}
OUT=loadtest/results
mkdir -p "$OUT"

scrape() { # $1 = replica container name -> selected Prometheus lines
  docker run --rm --network "$NETWORK" curlimages/curl:8.10.1 -s "http://$1:8081/actuator/prometheus" \
    | grep -E '^(tracker_websocket_sessions|tracker_realtime_published_total|tracker_realtime_fanout_fallback_total|hikaricp_connections_active|hikaricp_connections_pending|jvm_threads_live_threads|process_cpu_usage)' || true
}

# Sample open WebSocket sessions per replica every 10s (shows how sockets are spread).
(
  while true; do
    line="$(date +%T)"
    for r in tracker-backend-1 tracker-backend-2; do
      line="$line $r=$(scrape $r | awk '/^tracker_websocket_sessions/ {print $2}')"
    done
    echo "$line"
    sleep 10
  done
) > "$OUT/sessions-$RUN_ID.log" 2>&1 &
SAMPLER=$!
trap 'kill $SAMPLER 2>/dev/null || true' EXIT

# Sample container CPU/memory every 5s, to tell server saturation apart from load-generator saturation.
(
  while true; do
    docker stats --no-stream --format '{{.Name}} cpu={{.CPUPerc}} mem={{.MemUsage}}' | sed "s/^/$(date +%T) /"
    sleep 5
  done
) > "$OUT/docker-stats-$RUN_ID.log" 2>&1 &
STATS=$!
trap 'kill $SAMPLER $STATS 2>/dev/null || true' EXIT

set +e
docker run --rm --network "$NETWORK" \
  -v "$PWD/loadtest:/scripts:ro" -v "$PWD/$OUT:/results" \
  -e RUN_ID="$RUN_ID" -e VIEWERS="${VIEWERS:-200}" -e WRITERS="${WRITERS:-10}" -e READERS="${READERS:-10}" \
  -e DURATION="${DURATION:-2m}" ${WS_TARGETS:+-e WS_TARGETS="$WS_TARGETS"} \
  grafana/k6:0.54.0 run --quiet /scripts/board-load.js > "$OUT/k6-$RUN_ID.txt" 2>&1
K6_EXIT=$?
set -e

for r in tracker-backend-1 tracker-backend-2; do
  echo "== $r"; scrape "$r"
done > "$OUT/server-metrics-$RUN_ID.txt"
echo "Results: $OUT/*-$RUN_ID.*  (k6 exit $K6_EXIT; 99 = a threshold failed)"
exit $K6_EXIT
