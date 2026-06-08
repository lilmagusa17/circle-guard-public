# Design Patterns — CircleGuard

All design patterns implemented or identified in CircleGuard, grouped by category.

---

## Existing Patterns (found in codebase)

Documented in [`existing.md`](existing.md).

| Pattern | Service(s) | Category |
|---------|-----------|----------|
| [API Gateway](#) | gateway-service | Structural |
| [Database per Service](#) | All 8 services | Data |
| [Event-Driven / Kafka](#) | form, notification, promotion | Integration |
| [JWT Authentication](#) | auth-service, gateway-service | Security |
| [k-Anonymity Privacy Filter](#) | dashboard-service | Privacy |
| [Repository (Spring Data JPA)](#) | auth, dashboard, form, identity, promotion | Data Access |
| [Health Status State Machine](#) | promotion-service | Behavioral |

---

## New Patterns (implemented in Proyecto Final)

### Pattern 1 — Resilience: Circuit Breaker + Retry

**Document:** [`resilience.md`](resilience.md)  
**Implementation:** Istio `DestinationRule` (outlierDetection) + `VirtualService` (retries)  
**Files:** `k8s/istio/dev/destination-rules.yaml`, `k8s/istio/dev/virtual-services.yaml`

Prevents cascading failures by ejecting unhealthy service instances from the load-balancing pool after 5 consecutive 5xx errors. Retries transient failures (connect-failure, gateway-error) up to 3 times with a 2s per-try timeout.

**Why chosen over Resilience4j in code:** Zero application code changes. Policy is declared in YAML and applied uniformly to all 8 services. Changing the threshold affects all services without a code redeploy.

---

### Pattern 2 — Configuration: External Configuration (ESO)

**Document:** (this file, see below)  
**Implementation:** External Secrets Operator + GCP Secret Manager  
**Files:** `k8s/eso/`

Application secrets (DB passwords, JWT signing keys, SMTP credentials) are stored in GCP Secret Manager. The External Secrets Operator syncs them into Kubernetes `Secret` resources using Workload Identity — no service account key files needed in the cluster.

**Why chosen:** The alternative (plaintext secrets in k8s manifests) fails basic security audits. Secret Manager provides audit logging, versioning, and fine-grained IAM. Workload Identity removes the need to manage long-lived credentials.

**How it works:**
```
GCP Secret Manager
    └── cg-db-password-dev
            ↓ (ESO syncs every 1h)
K8s Secret: circleguard-db-credentials
    └── data.db-password
            ↓ (envFrom or secretKeyRef)
Pod env: SPRING_DATASOURCE_PASSWORD
```

---

### Pattern 3 — Sidecar (Istio Envoy Proxy)

**Document:** [`sidecar.md`](sidecar.md)  
**Implementation:** Istio sidecar injection on all 8 service pods  
**Files:** Istio mutating webhook + `k8s/istio/*/` configuration

Each application pod runs alongside an Envoy proxy container. The proxy handles mTLS, retries, circuit breaking, metrics, and distributed tracing — all without any changes to the Spring Boot application code.

**Why chosen:** The Sidecar pattern externalizes infrastructure concerns from the application. CircleGuard services have zero Resilience4j, zero Spring Cloud, zero TLS code. Operational concerns are declared in Istio configuration files, not compiled into JARs.

---

## Pattern Dependency Map

```
Sidecar (Envoy)
    ├── enables → Circuit Breaker (outlierDetection on sidecar traffic)
    ├── enables → Retry (VirtualService retries applied by sidecar)
    ├── enables → mTLS (transparent TLS between sidecars)
    └── enables → Distributed Tracing (trace header propagation)

External Configuration (ESO)
    └── feeds secrets into → all 8 services via K8s Secrets
```

---

## References

- [Resilience Patterns](resilience.md) — Circuit Breaker + Retry detail
- [Sidecar Pattern](sidecar.md) — Istio Envoy sidecar detail  
- [Service Mesh](service-mesh.md) — Full Istio mesh documentation
- [Existing Patterns](existing.md) — Patterns found in application code
