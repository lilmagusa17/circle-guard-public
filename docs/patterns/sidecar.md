# Sidecar Pattern — Istio Envoy Proxy

## What is it?

The Sidecar pattern deploys a helper container alongside every application container in the same pod. The helper handles infrastructure concerns that would otherwise pollute the application code.

In CircleGuard, Istio injects an Envoy proxy sidecar into every service pod automatically when the namespace has `istio-injection=enabled`.

## Where it is used

Every pod in `circleguard-dev`, `circleguard-stage`, and `circleguard-production` namespaces (all 8 microservices).

Verify:
```bash
kubectl get pods -n circleguard-dev -o jsonpath='{.items[*].spec.containers[*].name}'
# Each pod has 2 containers: app + istio-proxy
```

## What the sidecar offloads

| Concern | Without sidecar | With Istio sidecar |
|---------|----------------|-------------------|
| mTLS | Each service must manage TLS certs | Envoy handles TLS termination and cert rotation |
| Retries | Spring `@Retryable` or Resilience4j in code | Declarative retry in `VirtualService` — zero app code |
| Circuit breaker | Resilience4j dependency in every service | `outlierDetection` in `DestinationRule` — zero app code |
| Metrics | Micrometer + actuator endpoint in each service | Envoy emits HTTP metrics automatically (request rate, error rate, latency) |
| Distributed tracing | OpenTelemetry SDK in every service | Envoy propagates trace headers automatically |
| Traffic shaping | Not possible without code changes | VirtualService weights, canary, A/B testing |

## Implementation

Istio enables sidecar injection via namespace label:

```bash
kubectl label namespace circleguard-dev istio-injection=enabled
```

After restart, each pod spec shows:

```yaml
containers:
- name: auth-service          # application container
  image: magusa17/circleguard-auth:latest
- name: istio-proxy           # sidecar — injected by Istio mutating webhook
  image: docker.io/istio/proxyv2:1.24.3
```

The `holdApplicationUntilProxyStarts` annotation (applied to all service deployments) ensures Envoy is ready before Spring Boot starts connecting to databases:

```yaml
annotations:
  proxy.istio.io/config: '{"holdApplicationUntilProxyStarts": true}'
```

## Infrastructure pods are excluded

Kafka, PostgreSQL, Redis, Neo4j, and Zookeeper have `sidecar.istio.io/inject: "false"`. These images have environment-variable naming conflicts with Kubernetes service link injection (see CLAUDE.md Known Issues). They use `tcpSocket` probes to avoid Istio CNI iptables interference.

## Benefits

**Zero application code changes.** The 8 CircleGuard services have no Resilience4j, no Spring Retry for mesh concerns, no TLS code. All of that is externalized to the sidecar.

**Uniform policy enforcement.** mTLS STRICT mode is enforced at the infrastructure level. A developer cannot accidentally deploy a service that communicates in plaintext — the sidecar will refuse the connection.

**Operational observability.** Kiali reads Envoy metrics to build the service graph. No instrumentation needed in application code for the mesh-level view.

## Tradeoffs

- **Startup latency:** Each pod takes ~2-3s longer to start (Envoy initialization).
- **Resource overhead:** Each sidecar uses ~40-100 MB RAM and ~10m CPU. Critical for resource-constrained nodes (see production single-node limits in CLAUDE.md).
- **Complexity:** Probe rewriting (tcpSocket → HTTP via pilot-agent port 15020) causes 2-3 restart cycles on first deployment. Documented in CLAUDE.md Known Issues.

## Related patterns

- [`docs/patterns/resilience.md`](resilience.md) — Circuit Breaker + Retry implemented via the sidecar
- [`docs/patterns/service-mesh.md`](service-mesh.md) — Full Istio mesh documentation
