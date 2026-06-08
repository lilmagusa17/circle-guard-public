# Service Mesh — Istio

## What is implemented

CircleGuard uses Istio 1.24.3 (demo profile) across all 3 environments (dev/stage/production):

| Feature | Resource | File |
|---------|----------|------|
| mTLS STRICT | `PeerAuthentication` | `k8s/istio/{env}/peer-authentication.yaml` |
| Circuit Breaker | `DestinationRule` (outlierDetection) | `k8s/istio/{env}/destination-rules.yaml` |
| Retry Policy | `VirtualService` (retries) | `k8s/istio/{env}/virtual-services.yaml` |
| Canary Traffic Split | `DestinationRule` (subsets) + `VirtualService` (weights) | same |
| Ingress Gateway | `Gateway` | `k8s/istio/gateway.yaml` |
| Observability | Kiali + Jaeger + Prometheus + Grafana | installed from `samples/addons` |

## Why Istio over Linkerd

| Criterion | Istio | Linkerd |
|-----------|-------|---------|
| Traffic management | Full (weights, retries, CB, fault injection) | Limited (basic splitting) |
| GKE compatibility | Native | Good |
| Observability | Kiali + Jaeger built-in | Buoyant Cloud or manual |
| Canary support | Native VirtualService weights | Via SMI or flagger |
| Learning resources | Extensive | Growing |
| Complexity | Higher | Lower |

Istio was chosen because:
1. Phase 4 requires canary deployment via traffic weight — Istio's VirtualService supports this natively
2. Circuit breaker requirement (Phase 5) is directly met by Istio DestinationRule
3. The demo profile installs Kiali/Jaeger/Grafana/Prometheus as a single bundle
4. GKE + Istio is a well-documented combination with official support

## mTLS Strategy

All service-to-service communication within each namespace uses STRICT mTLS:
- Every pod gets an Envoy sidecar (namespace label `istio-injection=enabled`)
- `PeerAuthentication` with `STRICT` mode blocks all non-mTLS inbound connections
- Infrastructure services (Postgres, Kafka, Redis, Neo4j) do NOT get sidecars; Envoy passthrough mode handles outbound traffic to them

Certificate rotation is automatic via istiod (SPIFFE/X.509 certs, 24h TTL).

## Traffic Management Approach

- **Retry:** 3 attempts, 2s per-try timeout, on 5xx/gateway-error/connect-failure
- **Circuit Breaker:** Eject after 5 consecutive 5xx in 30s window; 30s ejection; up to 100% of pool
- **Canary:** VirtualService weight split on `gateway-service` (v1/v2 subsets); default 100/0

## Kiali Screenshots

See [`docs/diagrams/kiali-graph.png`](../diagrams/kiali-graph.png) — captured after all 8 services verified in mesh with mTLS lock icons.

## Operational notes

### Probe rewriting
Istio rewrites health probes in injected pods. Our tcpSocket probes are rewritten to HTTP probes by the pilot-agent. This causes readiness failures during Flyway DB migration (first run, ~90s). Pods recover automatically after Flyway completes. Use `holdApplicationUntilProxyStarts: true` on all pods.

### Infrastructure pods and mTLS
Postgres/Kafka/Redis/Neo4j don't have sidecars. Traffic from mesh pods to these services bypasses mTLS (Envoy passthrough). This is correct — forcing mTLS on databases would require injecting Envoy into every DB pod.

### Scale-up procedure
After `gcloud container clusters resize ... --num-nodes=1`:
1. Wait 3 min for nodes to register
2. Istio control plane pods restart automatically
3. Postgres emptyDir data is lost — recreate 5 databases (see `current-state.md`)
4. Service pods restart and inject sidecars
5. Wait ~2 min for Spring Boot startup + Flyway
