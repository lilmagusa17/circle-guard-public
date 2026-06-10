# Network Policies — Istio AuthorizationPolicy Topology

CircleGuard uses Istio `AuthorizationPolicy` resources instead of Kubernetes `NetworkPolicy` for traffic control. This provides L7 (application-layer) enforcement with identity-based access control via mTLS certificates.

---

## Policy Strategy

Each namespace (`circleguard-dev`, `circleguard-stage`, `circleguard-production`) has the same policy structure:

1. **Default deny-all**: An `AuthorizationPolicy` with an empty spec denies all traffic not explicitly allowed.
2. **Explicit allow policies**: Named policies permit specific source-to-destination paths.

This is a whitelist approach: anything not explicitly allowed is blocked.

---

## Allowed Service-to-Service Edges

### External Traffic

| Source | Destination | Port | Policy Name | Notes |
|--------|------------|------|-------------|-------|
| Internet (any) | Istio Ingress Gateway | 80 | (GKE LB, no AuthZ) | HTTP, redirected to HTTPS when cert-manager TLS is active |
| Internet (any) | Istio Ingress Gateway | 443 | (GKE LB, no AuthZ) | HTTPS via cert-manager Let's Encrypt cert |

The ingress gateway itself is in the `istio-system` namespace; it is not subject to the application namespace AuthorizationPolicy.

### Gateway to Backend Services

`allow-from-gateway` policy permits the gateway-service ServiceAccount to reach any service in the namespace.

| Source | Destination | Notes |
|--------|------------|-------|
| `gateway-service-sa` | `auth-service` | Route: `/api/auth/**` |
| `gateway-service-sa` | `dashboard-service` | Route: `/api/dashboard/**` |
| `gateway-service-sa` | `file-service` | Route: `/api/files/**` |
| `gateway-service-sa` | `form-service` | Route: `/api/forms/**`, `/api/surveys/**` |
| `gateway-service-sa` | `identity-service` | Route: `/api/identity/**` |
| `gateway-service-sa` | `notification-service` | Route: `/api/notifications/**` |
| `gateway-service-sa` | `promotion-service` | Route: `/api/promotion/**` |

### Auth Service (JWT Validation)

`allow-auth-service` policy permits any service in the namespace to call `auth-service`. This is required because multiple services validate tokens by calling auth-service directly.

| Source | Destination | Notes |
|--------|------------|-------|
| Any service in namespace | `auth-service:8180` | JWT token validation |

### Internal Service Communication

`allow-internal` policy permits all intra-namespace traffic and traffic from `istio-system`. This covers:

| Source | Destination | Notes |
|--------|------------|-------|
| `form-service` | Kafka broker | Produces `survey.submitted` events |
| `promotion-service` | Kafka broker | Produces `promotion.status.changed`, `circle.fenced` events |
| `notification-service` | Kafka broker | Consumes `alert.priority`, `survey.submitted` events |
| `promotion-service` | Kafka broker | Consumes `certificate.validated` events |
| `dashboard-service` | `promotion-service` | Status queries for hotspot analytics |
| All services | PostgreSQL | Each service connects to its own database schema |
| `gateway-service` | Redis | Session cache (QR token validation) |
| `promotion-service` | Redis | Health status cache |
| `promotion-service` | Neo4j | Graph traversal for contact network |

Note: Kafka, PostgreSQL, Redis, and Neo4j pods have `sidecar.istio.io/inject: "false"` — they are outside the mesh and accessible to all pods in the namespace without AuthorizationPolicy restriction.

### Observability Traffic

`allow-prometheus-scrape` policy permits the `monitoring` namespace to scrape metrics endpoints.

| Source | Destination | Paths |
|--------|------------|-------|
| `monitoring` namespace (Prometheus) | Any service | `/actuator/prometheus`, `/actuator/health` |

---

## Applying Policies

Policies are in namespace-specific files. Apply them with:

```bash
# Dev
kubectl apply -f k8s/istio/dev/authorization-policies.yaml

# Stage
kubectl apply -f k8s/istio/stage/authorization-policies.yaml

# Production
kubectl apply -f k8s/istio/production/authorization-policies.yaml
```

## Verifying Policies

```bash
# List all policies across all namespaces
kubectl get authorizationpolicy -A

# Show policy details for a specific namespace
kubectl get authorizationpolicy -n circleguard-production -o yaml

# Check if a specific request is allowed (requires istioctl)
istioctl x authz check <pod-name>.<namespace>
```

## Troubleshooting Blocked Traffic

If a service is returning 403 (RBAC: access denied), check:

1. Does the source pod have a sidecar? (`kubectl get pod <pod> -o jsonpath='{.spec.containers[*].name}'`)
2. What is the source pod's ServiceAccount? (`kubectl get pod <pod> -o jsonpath='{.spec.serviceAccountName}'`)
3. Is there an AuthorizationPolicy that covers the destination service with an ALLOW rule for that source?

To temporarily debug, add the source namespace to the `allow-internal` policy. Remove after debugging.

## Relationship to PeerAuthentication

AuthorizationPolicy works on top of mTLS. The `PeerAuthentication` resource (in `k8s/istio/*/peer-authentication.yaml`) enforces that all connections use mTLS. AuthorizationPolicy then checks the authenticated identity (SPIFFE URI derived from the ServiceAccount) against the ALLOW rules.

Both must be in place for full security:
- `PeerAuthentication`: encrypts and authenticates the connection
- `AuthorizationPolicy`: enforces which authenticated identities may connect to which services
