#!/bin/bash
# Smoke test: hit each service's health endpoint from inside the cluster.
# Usage: ./ci/smoke-test.sh <namespace>
# Requires kubectl to be configured and the tester pod to run curl.

set -euo pipefail

NAMESPACE="${1:-circleguard-dev}"

declare -a SERVICES=(
  "auth-service:8180"
  "dashboard-service:8084"
  "file-service:8085"
  "form-service:8086"
  "gateway-service:8087"
  "identity-service:8083"
  "notification-service:8082"
  "promotion-service:8088"
)

echo "=== CircleGuard Smoke Test — namespace: $NAMESPACE ==="
PASS=0
FAIL=0

for entry in "${SERVICES[@]}"; do
  SVC="${entry%%:*}"
  PORT="${entry##*:}"
  URL="http://${SVC}.${NAMESPACE}.svc.cluster.local:${PORT}/actuator/health"

  STATUS=$(kubectl run --rm -i --restart=Never \
    --image=curlimages/curl:8.6.0 \
    "smoketest-$(date +%s%N | tail -c6)" \
    -n "$NAMESPACE" \
    -- curl -s -o /dev/null -w "%{http_code}" --max-time 10 "$URL" 2>/dev/null || echo "000")

  if [ "$STATUS" = "200" ]; then
    echo "  PASS  $SVC ($PORT) → $STATUS"
    ((PASS++))
  else
    echo "  FAIL  $SVC ($PORT) → $STATUS"
    ((FAIL++))
  fi
done

echo ""
echo "Results: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
