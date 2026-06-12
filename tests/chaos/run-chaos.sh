#!/usr/bin/env bash
# Run CircleGuard chaos experiments one at a time, measuring steady-state
# before / during / after each so the recovery is provable, not assumed.
#
# Usage:
#   ./run-chaos.sh load        # start a background load generator first (separate terminal)
#   ./run-chaos.sh pod-kill    # run experiment 1
#   ./run-chaos.sh net-delay   # run experiment 2
#   ./run-chaos.sh cpu-stress  # run experiment 3
#   ./run-chaos.sh all         # run all three sequentially
#
# Requires: kubectl (context = circleguard-dev). Optional: a gateway URL for load.
# macOS bash 3.2 compatible.
set -euo pipefail

NS="circleguard-dev"
DIR="$(cd "$(dirname "$0")" && pwd)"
GATEWAY_URL="${GATEWAY_URL:-}"   # e.g. http://<istio-ingress-ip>

line(){ printf '%s\n' "----------------------------------------------------------------"; }

snapshot() {
  local label="$1"
  echo "[$label] pods in $NS:"
  kubectl get pods -n "$NS" -o wide --no-headers \
    | awk '{printf "    %-28s %-10s restarts=%s\n", $1, $3, $4}'
}

wait_recovery() {
  echo "Waiting for all pods Ready (max 180s)..."
  kubectl wait --for=condition=Ready pods --all -n "$NS" --timeout=180s \
    && echo "    recovered." || echo "    WARN: not all pods Ready in time."
}

run_experiment() {
  local file="$1" name="$2"
  line; echo ">>> EXPERIMENT: $name"; line
  snapshot "BEFORE"
  echo "Applying $file ..."
  kubectl apply -f "$DIR/$file"
  echo "Injecting fault — observe Grafana now. Sleeping 45s..."
  sleep 45
  snapshot "DURING"
  echo "Cleaning up fault..."
  kubectl delete -f "$DIR/$file" --ignore-not-found
  wait_recovery
  snapshot "AFTER"
  line; echo "<<< DONE: $name"; line; echo ""
}

load_gen() {
  [ -n "$GATEWAY_URL" ] || { echo "Set GATEWAY_URL=http://<ingress-ip> to drive load"; exit 1; }
  echo "Driving load at $GATEWAY_URL (Ctrl-C to stop)..."
  while true; do
    curl -s -o /dev/null -w "%{http_code} %{time_total}s\n" "$GATEWAY_URL/actuator/health" || true
    sleep 0.2
  done
}

case "${1:-all}" in
  load)       load_gen ;;
  pod-kill)   run_experiment "01-pod-kill-auth.yaml"        "Pod kill — auth-service" ;;
  net-delay)  run_experiment "02-network-delay-form.yaml"   "Network delay 500ms — form-service" ;;
  cpu-stress) run_experiment "03-cpu-stress-promotion.yaml" "CPU stress — promotion-service" ;;
  all)
    run_experiment "01-pod-kill-auth.yaml"        "Pod kill — auth-service"
    run_experiment "02-network-delay-form.yaml"   "Network delay 500ms — form-service"
    run_experiment "03-cpu-stress-promotion.yaml" "CPU stress — promotion-service"
    echo "All experiments complete. Record findings in docs/operations/chaos-results.md"
    ;;
  *) echo "usage: $0 {load|pod-kill|net-delay|cpu-stress|all}"; exit 1 ;;
esac
