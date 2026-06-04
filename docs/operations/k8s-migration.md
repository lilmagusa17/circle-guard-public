# K8s Migration — DigitalOcean → GKE

## Manifest Inventory

### Existing files (pre-migration)

| File | Status | Notes |
|------|--------|-------|
| `k8s/namespaces.yaml` | ✅ Updated | Renamed `circleguard-master` → `circleguard-production` |
| `k8s/infra/postgres.yaml` | 🟡 Kept (dev only) | emptyDir — no PVC needed |
| `k8s/infra/kafka.yaml` | 🟡 Kept (reference) | bitnami KRaft, replaced by Confluent+ZK in dev-infrastructure.yaml |
| `k8s/infra/redis.yaml` | 🟡 Kept (reference) | |
| `k8s/infra/dev-infrastructure.yaml` | ✅ New | Full dev infra: PG + DB init Job + ZK + Kafka + Redis + Neo4j |
| `k8s/infra/postgres-stage.yaml` | ⚠️ Superseded | Replaced by `k8s/stage/infrastructure.yaml` |
| `k8s/stage/infrastructure.yaml` | ✅ Kept | Already had full stack (PG, ZK, Kafka, Redis, Neo4j) |
| `k8s/production/infrastructure.yaml` | ✅ New | Full prod infra (copy of stage + circleguard-production namespace) |
| `k8s/dev/*.yaml` | ✅ Updated | Fixed images, ports, DB URLs, added auth + dashboard |
| `k8s/stage/*.yaml` | ✅ Updated | Fixed images, imagePullPolicy, ports, added auth + dashboard |
| `k8s/production/*.yaml` | ✅ New | 8 services for circleguard-production, replicas=2 |
| `k8s/master/` | ⚠️ Old (minikube) | Kept for reference; replaced by `k8s/production/` |

## GCP-Specific Changes

### StorageClass (Task 2.2)
**Not applicable.** No PersistentVolumeClaims exist. Postgres and Neo4j use `emptyDir` (acceptable for dev/stage; prod can be upgraded later with `standard-rwo` PVCs).

### LoadBalancer Annotations (Task 2.3)
**Not applicable.** All services are ClusterIP. No DO-specific annotations were present.

### Ingress Decision (Task 2.4)
**Deferred to Phase 3 (Istio Gateway).** Istio Gateway replaces Ingress for external traffic. For Phase 2 smoke tests, use `kubectl port-forward`. No GKE Ingress resource needed.

## Changes Made

### Image names
All images fixed from `circleguard/<svc>:tag` → `magusa17/circleguard-<svc>:latest`

### imagePullPolicy
Removed `imagePullPolicy: Never` (minikube-only). Using `Always` to ensure latest image is pulled from Docker Hub.

### Ports
Fixed from generic `8080` to actual service ports:

| Service | Port |
|---------|------|
| auth-service | 8180 |
| dashboard-service | 8084 |
| file-service | 8085 |
| form-service | 8086 |
| gateway-service | 8087 |
| identity-service | 8083 |
| notification-service | 8082 |
| promotion-service | 8088 |

### Health probes
Updated from `/actuator/health` → `/actuator/health/readiness` and `/actuator/health/liveness`. Added `livenessProbe` to all deployments.

### Database URLs
Each service now points to its own database:

| Service | Database |
|---------|----------|
| auth-service | `circleguard_auth` |
| dashboard-service | `circleguard_dashboard` |
| form-service | `circleguard_form` |
| promotion-service | `circleguard_promotion` |
| identity-service | `circleguard_identity` |

DB credentials: `admin` / `circleguard` (to be moved to Secret Manager in Phase 8).

A Kubernetes Job (`postgres-db-init`) creates all 5 databases on first deploy.

### Added missing services
`auth-service` and `dashboard-service` were missing from dev and stage. Added.

### Dev infrastructure
Added `k8s/infra/dev-infrastructure.yaml` with full stack: Postgres, DB init Job, Zookeeper, Kafka (Confluent), Redis, Neo4j.

## Deploy Commands

### Dev
```bash
kubectl apply -f k8s/namespaces.yaml
kubectl apply -f k8s/infra/dev-infrastructure.yaml
# Wait for postgres-db-init job to complete (~30s)
kubectl wait --for=condition=complete job/postgres-db-init -n circleguard-dev --timeout=120s
kubectl apply -f k8s/dev/
```

### Stage
```bash
kubectl apply -f k8s/namespaces.yaml
kubectl apply -f k8s/stage/infrastructure.yaml
kubectl apply -f k8s/stage/
```

### Production
```bash
kubectl apply -f k8s/namespaces.yaml
kubectl apply -f k8s/production/infrastructure.yaml
kubectl apply -f k8s/production/
```

## Smoke Test

```bash
# From inside cluster:
./ci/smoke-test.sh circleguard-dev
```
