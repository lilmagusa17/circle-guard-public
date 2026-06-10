# CircleGuard – Security Tests (OWASP ZAP)

This document describes the OWASP ZAP baseline scanning integration: what it scans, how to run it, where reports go, and when it becomes blocking.

---

## Overview

OWASP ZAP (Zed Attack Proxy) performs automated baseline security scans against CircleGuard's public-facing endpoints. The baseline scan checks for common vulnerabilities (OWASP Top 10 categories) without active attack — it is safe to run against a live dev environment.

---

## Script

**File:** `tests/security/zap-baseline.sh`

Uses the official ZAP Docker image (`ghcr.io/zaproxy/zaproxy:stable`).

```bash
# Run against the GKE dev ingress (default target)
bash tests/security/zap-baseline.sh

# Run against a custom target
ZAP_TARGET_URL=http://my-other-host bash tests/security/zap-baseline.sh
```

Environment variables:
| Variable        | Default                 | Description                    |
|-----------------|-------------------------|--------------------------------|
| `ZAP_TARGET_URL` | `http://34.58.31.128`  | Istio ingress gateway public IP |

---

## Scanned Endpoints

ZAP spiders from the target URL. With the Istio ingress gateway at `http://34.58.31.128`, it will attempt to discover and scan all publicly reachable paths. Known endpoints exposed through the gateway VirtualService:

| Service            | Path prefix             |
|--------------------|-------------------------|
| auth-service       | `/api/v1/auth/**`       |
| form-service       | `/api/v1/surveys/**`    |
| gateway-service    | `/api/v1/gate/**`       |
| dashboard-service  | `/api/v1/analytics/**`  |

Services that are not exposed via the ingress gateway (identity, file, notification, promotion) are not directly reachable from outside the mesh and are not scanned by ZAP.

---

## Pipeline Integration

ZAP runs in the `Security Tests (ZAP)` stage in `ci/Jenkinsfile.stage`, after `Deploy to GKE Stage`. It is currently **non-blocking** (`|| true` + `catchError(buildResult: 'SUCCESS')`).

```groovy
stage('Security Tests (ZAP)') {
    steps {
        catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
            sh 'bash tests/security/zap-baseline.sh || true'
        }
    }
    post {
        always {
            archiveArtifacts artifacts: 'tests/security/zap-report*.html',
                             allowEmptyArchive: true
        }
    }
}
```

Reports are archived as Jenkins build artifacts under `tests/security/zap-report-<timestamp>.html`.

---

## Accessing Reports in Jenkins

1. Open the stage pipeline build in Jenkins.
2. Click **Build Artifacts** in the left panel.
3. Download or open `zap-report-<timestamp>.html`.

The HTML report contains:
- Alert summary (FAIL / WARN / INFO counts)
- Per-alert details: description, affected URL, evidence, CWE reference, OWASP category
- Remediation guidance links

---

## Graduation Criteria (when to make blocking)

The ZAP stage will become blocking (removing `|| true` and setting `catchError` to `buildResult: 'FAILURE'`) when:

1. **Zero HIGH or CRITICAL ZAP alerts** on the baseline scan — verified for at least 2 consecutive successful stage builds.
2. All known false positives have been reviewed and suppressed in a ZAP context file (`tests/security/zap-context.xml`).
3. The dev team has triaged the initial ZAP report and confirmed that any remaining MEDIUM alerts are accepted risks (documented in `docs/operations/security.md`).

Until these criteria are met, ZAP findings are informational only. The stage build remains `SUCCESS` even if ZAP reports alerts.

---

## Running Manually

```bash
# Full run against GKE dev
ZAP_TARGET_URL=http://34.58.31.128 bash tests/security/zap-baseline.sh

# Run against local Docker Compose stack
ZAP_TARGET_URL=http://localhost:8087 bash tests/security/zap-baseline.sh
```

Requires Docker to be available and running. ZAP image is ~900 MB; first run downloads it.

---

## Known Limitations

- ZAP baseline does not authenticate — protected endpoints returning 401/403 are not scanned in depth.
- Dynamic content (Kafka-driven state changes) is not exercised by spider-based scanning.
- False positives are common for frameworks like Spring Boot (e.g., X-Content-Type-Options on certain paths). Review each WARN-level alert before acting.
