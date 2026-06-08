# Resilience Patterns — Circuit Breaker + Retry

CircleGuard uses Istio's traffic management layer to implement resilience patterns without modifying application code.

## Circuit Breaker (Istio DestinationRule)

**Why:** Prevents cascading failures. When a service starts returning 5xx errors, the circuit breaker ejects it from the load-balancing pool so upstream services stop sending traffic to the degraded instance.

**Configuration:** `k8s/istio/dev/destination-rules.yaml` (replicated in `stage/`, `production/`)

```yaml
trafficPolicy:
  connectionPool:
    tcp:
      maxConnections: 100
    http:
      h2UpgradePolicy: DO_NOT_UPGRADE
      http1MaxPendingRequests: 100
      maxRequestsPerConnection: 10
  outlierDetection:
    consecutive5xxErrors: 5
    interval: 30s
    baseEjectionTime: 30s
    maxEjectionPercent: 100
```

**Parameters:**
- `consecutive5xxErrors: 5` — eject after 5 consecutive 5xx within 30s
- `baseEjectionTime: 30s` — minimum ejection duration (doubles on repeated failures)
- `maxEjectionPercent: 100` — allows ejecting all hosts if all fail (prefer availability over partial degradation)
- `maxConnections: 100` — connection pool cap prevents resource exhaustion

**Benefit:** Services are protected from upstream failures. A failing notification-service won't exhaust form-service threads waiting for dead connections.

**Trade-offs:** False positives during GC pauses or slow Flyway startup can trigger temporary ejection. `interval: 30s` is conservative to avoid this.

## Retry Policy (Istio VirtualService)

**Why:** Transient failures (network blips, pod restarts during rolling updates) are retried transparently without the client seeing an error.

**Configuration:** `k8s/istio/dev/virtual-services.yaml`

```yaml
retries:
  attempts: 3
  perTryTimeout: 2s
  retryOn: 5xx,gateway-error,connect-failure,retriable-4xx
```

**Which endpoints get retries:** All service-to-service HTTP calls routed through the mesh. The `retryOn` conditions target infrastructure-level failures:
- `5xx` — server errors (Spring Boot unhandled exceptions)
- `gateway-error` — 502/503/504 from upstream
- `connect-failure` — TCP connection refused (pod restarting)
- `retriable-4xx` — 409 Conflict (idempotent retryable errors)

**Not retried:** Direct database operations, Kafka produce/consume. These are handled by the applications themselves via Hikari pool retry and Kafka consumer retry semantics.

**Benefit:** Rolling deployments are seamless — requests that hit a terminating pod are retried on a live pod.

**Trade-offs:** `perTryTimeout: 2s` is very short for Spring Boot endpoints that involve DB queries. Consider increasing to 5s for dashboard analytics calls if needed.

## Relationship to Sidecar Pattern

Both circuit breaker and retry are implemented entirely in the Envoy sidecar proxy. Zero lines of application code changed. See [`docs/patterns/sidecar.md`](sidecar.md) for the sidecar pattern documentation.
