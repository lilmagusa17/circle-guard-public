# Istio mTLS Verification

Verification procedure and results for CircleGuard service mesh mTLS enforcement.

## Cluster: circleguard-dev (2026-06-08)

### Configuration applied

- Istio 1.24.3, demo profile
- `PeerAuthentication` with `mtls.mode: STRICT` in `circleguard-dev` namespace
- All 8 microservices have Envoy sidecar injected (2/2 containers per pod)
- `holdApplicationUntilProxyStarts: true` on all pods (prevents app startup race with proxy)

### mTLS verification test

Test: attempt plain HTTP from a pod WITHOUT sidecar (sidecar.istio.io/inject=false) to auth-service.

```bash
kubectl run mtls-test --rm -it --restart=Never \
  --image=curlimages/curl:latest \
  -n circleguard-dev \
  --annotations='sidecar.istio.io/inject=false' \
  -- sh -c "curl -s -o /dev/null -w '%{http_code}' http://auth-service:8180/ --max-time 5"
```

**Result:** HTTP status `000` (connection reset/refused) — Envoy proxy in the target pod rejected the non-mTLS connection. STRICT mTLS is enforced.

### Sidecar injection confirmation

All 8 services show 2 containers (app + istio-proxy):

```
auth-service           2/2  Running
dashboard-service      2/2  Running
file-service           2/2  Running
form-service           2/2  Running
gateway-service        2/2  Running
identity-service       2/2  Running
notification-service   2/2  Running
promotion-service      2/2  Running
```

### Infrastructure pods (no sidecar)

Kafka, Postgres, Redis, Neo4j, Zookeeper do not have sidecar injection (deployed before namespace label was applied, and they should not get sidecars). These pods have 1/1 containers.

Services with sidecars connect to infrastructure pods via Envoy passthrough (no mTLS to non-mesh services). This works correctly — Hikari connection pools to Postgres and Kafka consumer connections confirmed working.

### Ingress Gateway

External IP: `34.58.31.128` (dev cluster)
Gateway routes port 80 → `gateway-service:8087` via VirtualService.

### How to re-verify after a cluster restart

```powershell
# 1. Check PeerAuthentication is STRICT
kubectl get peerauthentication -n circleguard-dev

# 2. Check all pods have 2 containers
kubectl get pods -n circleguard-dev

# 3. Run mTLS test
kubectl run mtls-test --rm --restart=Never --image=curlimages/curl:latest `
  -n circleguard-dev --annotations='sidecar.istio.io/inject=false' `
  -- sh -c "curl -s -o /dev/null -w '%{http_code}' http://auth-service:8180/ --max-time 5; echo"
# Expected: 000 (connection blocked)
```
