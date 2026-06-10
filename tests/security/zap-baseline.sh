#!/usr/bin/env bash
# OWASP ZAP Baseline scan against CircleGuard dev environment public endpoints.
# Requires Docker to be available.
set -euo pipefail

TARGET_URL="${ZAP_TARGET_URL:-http://34.58.31.128}"
REPORT_DIR="tests/security"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
REPORT_FILE="${REPORT_DIR}/zap-report-${TIMESTAMP}.html"

mkdir -p "${REPORT_DIR}"

echo "Running ZAP baseline scan against: ${TARGET_URL}"
docker run --rm \
    -v "$(pwd)/${REPORT_DIR}:/zap/wrk:rw" \
    -t ghcr.io/zaproxy/zaproxy:stable zap-baseline.py \
    -t "${TARGET_URL}" \
    -r "zap-report-${TIMESTAMP}.html" \
    -l WARN \
    --auto \
    -I || true

echo "ZAP report saved: ${REPORT_FILE}"
