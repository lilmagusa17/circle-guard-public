# CircleGuard Alerting Rules

All alert rules are defined in `k8s/monitoring/prometheus-rules.yaml` as a single `PrometheusRule` named `circleguard-alerts` in the `monitoring` namespace. Alerts are routed to Slack channel `#circleguard-alerts` via the `AlertmanagerConfig` in `k8s/monitoring/alertmanager-config.yaml`.

---

## Rule Groups

### Group: `circleguard.pods`

#### PodCrashLooping

| Field | Value |
|-------|-------|
| Severity | warning |
| Fires after | 5 minutes |
| Expression | `rate(kube_pod_container_status_restarts_total{namespace=~"circleguard-.*"}[5m]) > 0` |

**What it means:** A container inside a CircleGuard pod has restarted at least once in the past 5 minutes and continues to do so. This indicates an application error, OOM kill, or failed probe causing Kubernetes to restart the container.

**Suggested action:**
1. Identify the pod: `kubectl get pods -n <namespace> | grep -v Running`
2. Read recent logs: `kubectl logs <pod> -n <namespace> --previous`
3. Check pod events: `kubectl describe pod <pod> -n <namespace>`
4. Common causes: misconfigured environment variable, failed database connection at startup, liveness probe timeout.
5. If JVM OOM: check JVM heap alert below and increase memory limits.

---

#### PodNotReady

| Field | Value |
|-------|-------|
| Severity | critical |
| Fires after | 5 minutes |
| Expression | `kube_pod_status_ready{namespace=~"circleguard-.*", condition="true"} == 0` |

**What it means:** A CircleGuard pod has been in a not-ready state for more than 5 minutes. The pod is not receiving traffic. This is a critical issue that may indicate the service is completely unavailable.

**Suggested action:**
1. Check pod status: `kubectl get pods -n <namespace>`
2. Describe the pod for events: `kubectl describe pod <pod> -n <namespace>`
3. Check readiness probe configuration in the deployment manifest.
4. If Istio is involved, check whether the sidecar proxy started before the application: look for `holdApplicationUntilProxyStarts` annotation.
5. If database-dependent service: verify the database pod is Running and reachable.
6. Rollback if a recent deploy caused the issue: `kubectl rollout undo deployment/<service> -n <namespace>`

---

### Group: `circleguard.http`

#### HighErrorRate

| Field | Value |
|-------|-------|
| Severity | warning |
| Fires after | 5 minutes |
| Expression | `sum(rate(http_server_requests_seconds_count{namespace=~"circleguard-.*",outcome="SERVER_ERROR"}[5m])) / sum(rate(http_server_requests_seconds_count{namespace=~"circleguard-.*"}[5m])) > 0.05` |

**What it means:** More than 5% of all HTTP requests across CircleGuard services are returning 5xx (Server Error) responses. This typically indicates a bug, a downstream service failure, or resource exhaustion.

**Suggested action:**
1. Open Grafana > CircleGuard Overview > Error Rate panel to identify the offending service.
2. Search Kibana for `level: ERROR` in the `circleguard-*` index to find stack traces.
3. Open Jaeger, search for failed traces on the affected service.
4. Check if the error correlates with a recent deployment using the Kiali graph.
5. If a dependency (database, Kafka) is down, fix the dependency first.
6. If a recent code change is responsible, rollback: `kubectl rollout undo deployment/<service> -n <namespace>`

---

#### HighLatencyP95

| Field | Value |
|-------|-------|
| Severity | warning |
| Fires after | 10 minutes |
| Expression | `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{namespace=~"circleguard-.*"}[5m])) by (le, service)) > 1` |

**What it means:** The 95th percentile response time for at least one CircleGuard service exceeds 1 second and has done so for 10 consecutive minutes. Users are experiencing slow responses.

**Suggested action:**
1. Identify the slow service in Grafana > P95 Latency panel.
2. Check JVM GC pauses: look for `jvm_gc_pause_seconds` metrics in Prometheus. Long GC pauses (>200ms) indicate memory pressure.
3. Check database query times: look at Jaeger traces for slow DB spans.
4. Check node CPU and memory: `kubectl top nodes` and `kubectl top pods -n <namespace>`.
5. If GC-related: increase JVM heap limits in the deployment manifest.
6. If DB-related: check `pg_stat_activity` on Postgres for long-running queries.

---

### Group: `circleguard.jvm`

#### HighJvmHeap

| Field | Value |
|-------|-------|
| Severity | critical |
| Fires after | 5 minutes |
| Expression | `sum(jvm_memory_used_bytes{namespace=~"circleguard-.*",area="heap"}) by (pod) / sum(jvm_memory_max_bytes{namespace=~"circleguard-.*",area="heap"}) by (pod) > 0.90` |

**What it means:** A CircleGuard pod is using more than 90% of its configured JVM heap. This is a critical condition — if heap reaches 100%, the JVM throws `OutOfMemoryError` and the pod crashes. This often precedes a `PodCrashLooping` alert.

**Suggested action:**
1. Identify the pod: check the Grafana > JVM Heap Usage gauge.
2. Immediate mitigation: restart the pod to free memory temporarily: `kubectl rollout restart deployment/<service> -n <namespace>`
3. Investigate root cause: check for memory leaks using Jaeger traces for objects not being released.
4. Increase JVM heap: add `-Xmx` JVM flag via environment variable in the deployment manifest (e.g., `JAVA_OPTS: "-Xmx512m"`).
5. Increase container memory limit in the deployment manifest to match the new heap size plus overhead (~256MB for JVM non-heap).
6. Long-term: profile the service with async-profiler to identify the source of the memory growth.

---

## Alertmanager Routing

All alerts use a single route:
- **groupBy**: `alertname`, `namespace` — alerts for the same rule+namespace are batched
- **groupWait**: 30s — initial delay before sending the first notification
- **groupInterval**: 5m — minimum time between notifications for the same group
- **repeatInterval**: 4h — notifications repeat every 4 hours if unresolved

**Receiver**: `slack-receiver` — posts to `#circleguard-alerts` with alert name, summary, severity, and namespace. Sends a resolved notification when the condition clears.

### Creating the Slack webhook secret

Before applying `alertmanager-config.yaml`, create the secret in the `monitoring` namespace:

```bash
kubectl create secret generic alertmanager-slack \
  --from-literal=webhook-url=https://hooks.slack.com/services/YOUR/SLACK/WEBHOOK \
  -n monitoring
```

This webhook URL comes from a Slack app configured for the `#circleguard-alerts` channel. The same channel used by the Jenkins pipeline failure notifications (see `docs/operations/notifications.md`).
