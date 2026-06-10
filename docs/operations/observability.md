# CircleGuard Observability Runbook

This document describes the full observability stack for CircleGuard and explains how to access each tool, what data is available, and how to investigate incidents.

---

## Overview

CircleGuard uses four complementary observability tools:

| Layer | Tool | Namespace | Purpose |
|-------|------|-----------|---------|
| Metrics | Prometheus + Grafana | `monitoring` | Time-series metrics, dashboards, alerting |
| Logs | Elasticsearch + Kibana + Fluent Bit | `logging` | Structured log aggregation and search |
| Traces | Jaeger | `monitoring` (standalone) / `istio-system` (Istio) | Distributed request tracing |
| Mesh | Kiali | `istio-system` | Istio service graph, mTLS status, traffic flow |
| Alerts | Alertmanager | `monitoring` | Routing alerts to Slack (`#circleguard-alerts`) |

Istio Jaeger (`istio-system`) captures mesh-level spans automatically. The standalone Jaeger in `monitoring` accepts OTLP from Spring Boot services via OpenTelemetry.

---

## Accessing the Tools

All tools are accessed via `kubectl port-forward`. There are no public-facing UIs by default.

### Grafana
```bash
kubectl port-forward svc/kube-prometheus-stack-grafana 3000:80 -n monitoring
```
Open: http://localhost:3000
Credentials: `admin` / `circleguard-grafana`

Navigate to **Dashboards > CircleGuard** folder for per-service and overview dashboards.

### Prometheus
```bash
kubectl port-forward svc/kube-prometheus-stack-prometheus 9090:9090 -n monitoring
```
Open: http://localhost:9090

### Alertmanager
```bash
kubectl port-forward svc/kube-prometheus-stack-alertmanager 9093:9093 -n monitoring
```
Open: http://localhost:9093

### Kibana
```bash
kubectl port-forward svc/kibana-kibana 5601:5601 -n logging
```
Open: http://localhost:5601
No authentication (xpack.security disabled for dev).

First-time setup:
1. Go to Stack Management > Index Patterns
2. Create index pattern: `circleguard-*`
3. Set time field to `@timestamp`

### Jaeger (standalone)
```bash
kubectl port-forward svc/jaeger-query 16686:16686 -n monitoring
```
Open: http://localhost:16686

### Jaeger (Istio mesh traces)
```bash
kubectl port-forward svc/tracing 16686:80 -n istio-system
```
Open: http://localhost:16686

### Kiali
```bash
istioctl dashboard kiali
```
Or:
```bash
kubectl port-forward svc/kiali 20001:20001 -n istio-system
```
Open: http://localhost:20001

---

## Available Metrics

All CircleGuard services expose `/actuator/prometheus` (once `spring-boot-starter-actuator` and Micrometer Prometheus are added). The following metric families are available:

### HTTP Metrics (Spring Boot / Micrometer)
| Metric | Description |
|--------|-------------|
| `http_server_requests_seconds_count` | Total HTTP requests by method, URI, status, outcome |
| `http_server_requests_seconds_sum` | Total time spent serving requests |
| `http_server_requests_seconds_bucket` | Histogram buckets for latency percentiles |

### JVM Metrics
| Metric | Description |
|--------|-------------|
| `jvm_memory_used_bytes{area="heap"}` | Current heap usage |
| `jvm_memory_max_bytes{area="heap"}` | Max heap configured |
| `jvm_gc_pause_seconds_*` | GC pause duration histograms |
| `jvm_threads_live_threads` | Active thread count |

### Business Metrics (to be implemented per service)
| Metric | Service | Description |
|--------|---------|-------------|
| `surveys_submitted_total` | form-service | Health survey submissions |
| `files_uploaded_total` | file-service | File upload count |
| `notifications_sent_total` | notification-service | Notifications dispatched |
| `health_status_transitions_total` | promotion-service | Status lifecycle changes |

### Node and Pod Metrics (kube-state-metrics + node-exporter)
- `kube_pod_container_status_restarts_total` — pod restart counts
- `kube_pod_status_ready` — pod readiness
- `node_memory_MemAvailable_bytes` — node memory
- `node_cpu_seconds_total` — node CPU usage

---

## Key Prometheus Queries

### Request rate per service (last 5 min)
```promql
sum(rate(http_server_requests_seconds_count{namespace=~"circleguard-.*"}[5m])) by (job)
```

### Error rate (fraction of 5xx responses)
```promql
sum(rate(http_server_requests_seconds_count{namespace=~"circleguard-.*",outcome="SERVER_ERROR"}[5m]))
/
sum(rate(http_server_requests_seconds_count{namespace=~"circleguard-.*"}[5m]))
```

### P95 latency per service
```promql
histogram_quantile(0.95,
  sum(rate(http_server_requests_seconds_bucket{namespace=~"circleguard-.*"}[5m])) by (le, job)
)
```

### JVM heap utilization per pod
```promql
sum(jvm_memory_used_bytes{namespace=~"circleguard-.*",area="heap"}) by (pod)
/
sum(jvm_memory_max_bytes{namespace=~"circleguard-.*",area="heap"}) by (pod)
```

### Pods not ready
```promql
kube_pod_status_ready{namespace=~"circleguard-.*", condition="true"} == 0
```

### Pod restart rate (crash-looping detection)
```promql
rate(kube_pod_container_status_restarts_total{namespace=~"circleguard-.*"}[5m]) > 0
```

---

## Investigating an Incident

### Step 1: Check metrics in Grafana
1. Open the **CircleGuard Overview** dashboard.
2. Identify which service has elevated error rate or latency.
3. Check the **Pod Restart Count** panel for recent restarts.

### Step 2: Find traces in Jaeger
1. Open Jaeger UI (port-forward 16686).
2. Select the **Service** from the dropdown (e.g., `form-service`).
3. Set the time range to match when the incident occurred.
4. Click **Find Traces**.
5. Look for traces with errors (red spans). Click a trace to see the full call chain across services.
6. For Kafka-based flows (form-service → notification-service), look for spans with `messaging.system=kafka`.

### Step 3: Search logs in Kibana
1. Open Kibana > **Discover**.
2. Select the `circleguard-*` index pattern.
3. Filter by time range matching the incident.
4. Add a filter: `kubernetes.labels.app: <service-name>` (e.g., `form-service`).
5. Search for `level: ERROR` or `ERROR` to find stack traces.
6. Use the **Saved Searches** (one per service) for quick access.

Common Kibana filters:
```
kubernetes.namespace: circleguard-dev AND level: ERROR
kubernetes.labels.app: auth-service AND message: "authentication failed"
```

### Step 4: Check Istio mesh in Kiali
1. Open Kiali dashboard.
2. Select the **circleguard-dev** (or affected) namespace.
3. View the **Graph** tab — edges with red color indicate errors.
4. Check that all edges have the mTLS lock icon (padlock).
5. Click on a service node for its traffic details and health status.

---

## Alertmanager Rules Summary

See `k8s/monitoring/prometheus-rules.yaml` for the full rule definitions. All alerts fire to `#circleguard-alerts` Slack channel.

| Alert | Trigger | Severity | Action |
|-------|---------|----------|--------|
| `PodCrashLooping` | Any pod restarts in `circleguard-*` namespace | Warning | Check logs for the crashing container |
| `PodNotReady` | Pod not-ready for 5+ minutes | Critical | Inspect pod events and logs immediately |
| `HighErrorRate` | >5% of HTTP responses are 5xx | Warning | Check Jaeger traces for failing requests |
| `HighLatencyP95` | P95 latency > 1s for 10+ minutes | Warning | Check GC pauses, database query times |
| `HighJvmHeap` | JVM heap > 90% for 5+ minutes | Critical | Consider restarting pod; investigate memory leaks |

### Silence an alert during maintenance
```bash
# Via Alertmanager UI
kubectl port-forward svc/kube-prometheus-stack-alertmanager 9093:9093 -n monitoring
# Navigate to http://localhost:9093 > Silences > New Silence
```

---

## Infrastructure Deployment

All observability components are deployed via Helm:

```bash
# Install metrics stack (Prometheus + Grafana + Alertmanager)
bash k8s/monitoring/install.sh

# Install logging stack (Elasticsearch + Kibana + Fluent Bit)
bash k8s/logging/install.sh

# Apply ServiceMonitors, PrometheusRules, AlertmanagerConfig
kubectl apply -f k8s/monitoring/servicemonitors.yaml
kubectl apply -f k8s/monitoring/prometheus-rules.yaml
kubectl apply -f k8s/monitoring/alertmanager-config.yaml

# Deploy standalone Jaeger
kubectl apply -f k8s/tracing/jaeger.yaml
```

The Alertmanager Slack secret must be created before applying the AlertmanagerConfig:
```bash
kubectl create secret generic alertmanager-slack \
  --from-literal=webhook-url=https://hooks.slack.com/services/YOUR/WEBHOOK/URL \
  -n monitoring
```
