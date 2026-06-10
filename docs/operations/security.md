# Security Review — CircleGuard

Last updated: 2026-06-09

---

## Threat Model Summary

### External Threats

| Threat | Surface | Mitigation |
|--------|---------|------------|
| Unauthenticated API access | Istio Ingress Gateway (port 80/443) | JWT validation enforced at gateway-service; requests without valid token are rejected |
| Token forgery / replay | All REST endpoints | JWT signed with HS256, expiry enforced; Redis-backed session revocation in gateway-service |
| DDoS / rate abuse | Public IP 34.58.31.128 | GKE external LB with connection limits; Istio connection pool limits in DestinationRules |
| Image supply chain compromise | Docker Hub images | Trivy scan on every CI build (HIGH/CRITICAL reported); daily CronJob scan in production |
| Container escape | Node level | GKE node auto-upgrade enabled; containers run as non-root |

### Internal Threats (Lateral Movement)

| Threat | Surface | Mitigation |
|--------|---------|------------|
| Compromised pod reaching other services | Pod-to-pod communication | Istio STRICT mTLS — no plaintext internal traffic |
| Compromised pod reading secrets | K8s API | RBAC: each service account limited to `get`/`list` on its own namespace secrets only |
| Rogue workload injecting traffic | Service mesh | Istio AuthorizationPolicy: default-deny + explicit allows; deny-all baseline in every namespace |
| Plaintext DB credentials | K8s Secrets | External Secrets Operator syncs from GCP Secret Manager; no plaintext values committed |

---

## Mitigations in Place

### 1. Istio Mutual TLS (STRICT mode)

All three namespaces (`circleguard-dev`, `circleguard-stage`, `circleguard-production`) enforce STRICT mTLS via `PeerAuthentication` resources. Every pod-to-pod connection is encrypted and mutually authenticated with x.509 certificates rotated automatically by Istio.

Verify:
```bash
kubectl get peerauthentication -A
# Expected: STRICT mode in all circleguard-* namespaces
```

### 2. JWT Authentication

The `gateway-service` validates JWTs on every inbound request. Services behind the gateway trust the gateway's forwarded identity headers. Auth-service issues JWTs signed with a secret stored in GCP Secret Manager (never in code or environment variables).

### 3. RBAC — Least Privilege Service Accounts

Each of the 8 microservices has its own `ServiceAccount` with a `Role` granting only `get` and `list` on `configmaps` and `secrets` within its own namespace.

Manifests:
- `k8s/rbac/dev/rbac.yaml`
- `k8s/rbac/stage/rbac.yaml`
- `k8s/rbac/production/rbac.yaml`

### 4. Istio AuthorizationPolicy — Default-Deny

Every namespace has a `deny-all` `AuthorizationPolicy` (empty spec). Explicit `ALLOW` policies permit only documented service-to-service edges:
- External traffic → gateway-service (via Istio Ingress)
- gateway-service → all backend services
- Any service → auth-service (JWT validation)
- Intra-namespace traffic for Kafka consumers (form → Kafka → notification/promotion)
- Prometheus scraping from `monitoring` namespace

Manifests:
- `k8s/istio/dev/authorization-policies.yaml`
- `k8s/istio/stage/authorization-policies.yaml`
- `k8s/istio/production/authorization-policies.yaml`

Verify:
```bash
kubectl get authorizationpolicy -A
```

### 5. External Secrets Operator (ESO)

All Kubernetes `Secret` resources are sourced from GCP Secret Manager via ESO `ExternalSecret` resources. No plaintext secrets are stored in the repository or Kubernetes manifests.

Verify:
```bash
# Should return only ExternalSecret references, no raw password values
grep -rE "password:|secret:" k8s/
```

ESO manifests: `k8s/eso/{dev,stage,production}/`

### 6. Continuous Vulnerability Scanning (Trivy)

Two layers:
1. **CI pipeline**: `trivy image --severity HIGH,CRITICAL` runs on every Docker build in `ci/Jenkinsfile.dev`, `ci/Jenkinsfile.stage`, `ci/Jenkinsfile.master`. Currently non-blocking (`--exit-code 0`) to allow builds to proceed while known Spring Boot 3.2.4 CVEs are tracked.
2. **Daily production scan**: `CronJob` at 06:00 UTC scans all 8 production images and reports findings to Slack. Manifest: `k8s/security/trivy-scan-cronjob.yaml`.

### 7. TLS on Public Endpoints (cert-manager)

`cert-manager` v1.14.4 with Let's Encrypt integration manages TLS certificates for the Istio Ingress Gateway. The `ClusterIssuer` uses HTTP-01 challenge via Istio ingress class.

Installation script: `k8s/security/cert-manager-install.sh`
TLS Gateway resource: `k8s/security/gateway-tls.yaml`

Note: Let's Encrypt requires a valid FQDN. Update `circleguard.example.com` in `gateway-tls.yaml` to the actual domain before applying in production.

### 8. OWASP ZAP Baseline Scan

Automated ZAP baseline scans run as part of the stage pipeline post-deploy stage. Script: `tests/security/zap-baseline.sh`. Results are published as Jenkins artifacts. Currently non-blocking; graduation criteria documented in `docs/operations/security-tests.md`.

---

## Known Gaps and What Is Not Covered

| Gap | Risk | Planned Fix |
|-----|------|-------------|
| Spring Boot 3.2.4 CVEs (Tomcat 10.1.19, Spring Security 6.2.3) | HIGH | Upgrade to Spring Boot 3.2.12+ |
| Secret rotation automation | MEDIUM | ESO sync interval is 1h; no automatic rotation trigger on compromise |
| Supply chain security beyond Trivy | MEDIUM | No SBOM generation, no Sigstore/Cosign image signing |
| DNS-level security | LOW | No DNSSEC on domain; GCP Cloud DNS does not enforce DNSSEC by default |
| Pod Security Standards | LOW | GKE enforces baseline PSS by default; restricted profile not yet applied |
| Network egress control | LOW | No egress NetworkPolicy; services can reach external internet (needed for LDAP, mail) |
| Kubernetes API audit logging | LOW | GKE audit logs enabled at cluster level; not yet shipped to Kibana |

---

## Compliance Notes

- **FERPA**: Student identities are stored only in `identity-service` (PostgreSQL `circleguard_identity`). The contact graph in Neo4j uses salted hashes only.
- **Data retention**: Neo4j edges have a 14-day TTL enforced by the promotion-service.
- **Right to erasure**: Identity vault supports full purge via `identity-service` API.
