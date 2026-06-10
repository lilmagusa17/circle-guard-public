# Operations Manual — CircleGuard

Index of all operational documentation. Each entry includes a one-line description and a link to the full document.

---

## Live System State

| Document | Description |
|----------|-------------|
| [`current-state.md`](current-state.md) | Live record of deployed clusters, namespaces, and which phases are complete. Read this first at the start of every session. |

---

## Infrastructure & Deployment

| Document | Description |
|----------|-------------|
| [`k8s-migration.md`](k8s-migration.md) | Notes from migrating Kubernetes manifests from DigitalOcean to GKE; what was changed and why. |
| [`canary-deployments.md`](canary-deployments.md) | How to trigger, monitor, promote, and roll back a canary deployment using Istio VirtualService weight splits. |
| [`rollback.md`](rollback.md) | Exact `kubectl` commands for rolling back any service in any namespace; includes Istio canary reversal and a documented production rollback drill. |
| [`versioning.md`](versioning.md) | Semantic versioning convention (vMAJOR.MINOR.PATCH), bump rules from Conventional Commits, and how the master pipeline generates tags and GitHub Releases. |
| [`change-management.md`](change-management.md) | Formal CM process: who requests changes, who approves, what quality gates exist, and how the full deploy flow works from branch to production. |

---

## Security

| Document | Description |
|----------|-------------|
| [`security.md`](security.md) | Threat model summary, mitigations in place (mTLS, JWT, RBAC, ESO, Trivy, ZAP), known gaps, and compliance notes. |
| [`network-policies.md`](network-policies.md) | Istio AuthorizationPolicy topology: all allowed service-to-service edges documented, with apply and verify commands. |

---

## Observability

| Document | Description |
|----------|-------------|
| [`observability.md`](observability.md) | How to access Prometheus, Grafana, Kibana, Jaeger, and Kiali; common queries; overview of what metrics/logs/traces each tool provides. |
| [`alerts.md`](alerts.md) | PrometheusRule alerting definitions: pod restart loop, high latency, error rate, JVM heap, and PVC fullness thresholds. |
| [`istio-verification.md`](istio-verification.md) | Steps to verify Istio mTLS is enforced; includes test from inside and outside the mesh. |

---

## Testing & Quality

| Document | Description |
|----------|-------------|
| [`test-inventory.md`](test-inventory.md) | Complete catalog of unit, integration, E2E, and performance tests per service. |
| [`coverage-policy.md`](coverage-policy.md) | JaCoCo coverage thresholds enforced in the pipeline (line coverage >= 60%); how to view the aggregated report. |
| [`security-tests.md`](security-tests.md) | OWASP ZAP baseline scan setup, how to run it, what findings are acceptable, and graduation criteria for making it blocking. |
| [`test-results.md`](test-results.md) | Summary of the last successful master pipeline run: unit/integration/E2E counts, coverage %, Locust performance numbers, ZAP findings. |

---

## Cost & Performance

| Document | Description |
|----------|-------------|
| [`costs.md`](costs.md) | Monthly GCP cost estimates per environment based on actual configuration; cost reduction recommendations. |

---

## CI/CD Notifications

| Document | Description |
|----------|-------------|
| [`notifications.md`](notifications.md) | Slack webhook configuration for pipeline failure and success notifications; how to update the Jenkins credential. |

---

## How to Use This Manual

1. **Starting a session**: Read `current-state.md` first. It tells you what is deployed and what is not.
2. **Deploying a change**: Follow `change-management.md` for the process and quality gates.
3. **Something broke in production**: Go to `rollback.md` for exact commands.
4. **Investigating an incident**: Use `observability.md` to find logs, metrics, and traces.
5. **Security question**: Start with `security.md` for the threat model, then `network-policies.md` for the specific topology.
