# CircleGuard — Current Deployed State

Live record of what is actually deployed. Update this file every time infrastructure changes.

**Last updated:** 2026-06-08

---

## GCP Project

- **Project ID:** `circleguard-final`
- **Region:** `us-central1`

## Terraform State Bucket

- **Bucket:** `gs://circle-guard-tfstate-final`
- **Status:** ✅ Created, versioning enabled

## GKE Clusters

| Cluster | Status | Nodes | Notes |
|---------|--------|-------|-------|
| `circleguard-dev` | ✅ Running | 0 (scaled down) | All 13 pods verified, Istio installed |
| `circleguard-stage` | ✅ Running | 0 (scaled down) | All 13 pods verified, Istio installed |
| `circleguard-prod` | ✅ Running | 1 | All 13 pods 2/2 Running with Istio sidecars |

## Docker Hub Images (published)

All 8 images at `magusa17/circleguard-{auth,dashboard,file,form,gateway,identity,notification,promotion}:latest`
Built and pushed 2026-06-04.

## Kubernetes Namespaces

| Namespace | Status |
|-----------|--------|
| `circleguard-dev` | ✅ Created, all pods verified, Istio sidecar injection enabled |
| `circleguard-stage` | ✅ Created, all pods verified, Istio sidecar injection enabled |
| `circleguard-production` | ✅ Created, all 8 services 2/2 Running with Istio sidecars |

## Infrastructure per namespace

Deployed: Postgres (with DB init), Zookeeper, Kafka, Redis, Neo4j  
Databases: `circleguard_{auth,dashboard,form,promotion,identity}` created in each env

**Important:** All infra pods have `sidecar.istio.io/inject: "false"` annotation to prevent Istio CNI interference with exec probes.

## Istio Service Mesh (Phase 3)

| Component | Dev | Stage | Prod |
|-----------|-----|-------|------|
| Istio version | 1.24.3 | 1.24.3 | 1.24.3 |
| Profile | demo | demo | demo |
| Sidecar injection | ✅ Enabled | ✅ Enabled | ✅ Enabled |
| PeerAuthentication | ✅ STRICT | ✅ STRICT | ✅ STRICT |
| VirtualServices (8) | ✅ Applied | ✅ Applied | ✅ Applied |
| DestinationRules (8) | ✅ Applied | ✅ Applied | ✅ Applied |
| Gateway | ✅ Applied | ✅ Applied | ✅ Applied |
| Kiali | ✅ Running | ✅ Installed | ✅ Running |
| Jaeger | ✅ Running | ✅ Installed | ✅ Running |
| Prometheus | ✅ Running | ✅ Installed | ✅ Running |
| Grafana | ✅ Running | ✅ Installed | ✅ Running |

**Dev ingress gateway IP:** `34.58.31.128`

## Phase Completion

| Phase | Status | Notes |
|-------|--------|-------|
| Phase 0 — Foundation | 🔴 Not started | GCP access done manually; board/docs pending |
| Phase 1 — Terraform | 🟢 Done | All 16 tasks complete |
| Phase 2 — K8s Migration | 🟢 Done | All 12 tasks complete |
| Phase 3 — Istio | 🟡 In progress | Tasks 3.1-3.10, 3.12-3.14 done; 3.11 (Kiali screenshot) pending browser access |
| Phase 4 — CI/CD | 🔴 Not started | Needs Phase 2+3 |
| Phase 5 — Patterns | 🔴 Not started | Needs Phase 3 |
| Phase 6 — Testing | 🔴 Not started | Needs Phase 4 |
| Phase 7 — Observability | 🔴 Not started | Needs Phase 2+3 |
| Phase 8 — Security | 🔴 Not started | Needs Phase 3+4 |
| Phase 9 — Change Mgmt | 🔴 Not started | Needs Phase 4 |
| Phase 10 — Docs | 🔴 Not started | Needs all phases |

## Scale Up Commands (before working)

```powershell
$env:PATH = "C:\Users\Mariana\AppData\Local\Google\Cloud SDK\google-cloud-sdk\bin;$env:PATH"
$gcloud = "C:\Users\Mariana\AppData\Local\Google\Cloud SDK\google-cloud-sdk\bin\gcloud.cmd"
# Scale up dev only (quota: max 1 cluster with nodes at a time)
& $gcloud container clusters resize circleguard-dev --node-pool=default-pool --num-nodes=1 --region=us-central1 --project=circleguard-final --quiet

# Set kubectl context
$env:KUBECONFIG = "$env:USERPROFILE\.kube\circleguard-dev"
$env:USE_GKE_GCLOUD_AUTH_PLUGIN = "True"
```

## Scale Down Commands (after working)

```powershell
& $gcloud container clusters resize circleguard-dev --node-pool=default-pool --num-nodes=0 --region=us-central1 --project=circleguard-final --quiet
& $gcloud container clusters resize circleguard-stage --node-pool=default-pool --num-nodes=0 --region=us-central1 --project=circleguard-final --quiet
& $gcloud container clusters resize circleguard-prod --node-pool=default-pool --num-nodes=0 --region=us-central1 --project=circleguard-final --quiet
```

## Important Operational Notes

### Postgres databases (emptyDir — lost on pod restart)
When postgres pod restarts, all data is lost and the 5 service databases must be recreated:
```powershell
$pgpod = kubectl get pod -n circleguard-dev -l app=postgres -o jsonpath='{.items[0].metadata.name}'
foreach ($db in @("circleguard_auth","circleguard_dashboard","circleguard_form","circleguard_promotion","circleguard_identity")) {
  kubectl exec -n circleguard-dev $pgpod -- psql -U admin -d circleguard -c "CREATE DATABASE $db;"
}
```
**Production has a DB init Job** that runs automatically — but may need manual creation if the job fails.

### Istio sidecar injection startup
After scaling up a cluster with Istio:
1. Namespace label `istio-injection=enabled` is already applied (persists)
2. Istio control plane pods restart automatically
3. Postgres emptyDir data is lost — recreate 5 databases manually
4. Service pods restart and inject sidecars
5. Wait 2-3 minutes for Spring Boot + Flyway startup
6. Pods may restart 2-3 times during Flyway on first run (liveness probe rewriting) — this is normal

### Infrastructure pods and Istio
All infra pods (postgres, kafka, redis, neo4j, zookeeper) have `sidecar.istio.io/inject: "false"` to prevent Istio CNI from intercepting exec probes. Probes use `tcpSocket` not `exec` to avoid iptables interference.

### Service startup time
Spring Boot services take 75-90s to start on Spot VMs. Do not check pod readiness before 2 minutes after deploy.

### Production single-node resource limit
Production runs on 1 node (2 vCPUs, 8 GB RAM). All services are `replicas: 1`. DO NOT set replicas > 1 unless cluster has 2+ nodes. Node maxes out at ~13 pods with Istio sidecars.

### Kiali dashboard access
```powershell
$env:KUBECONFIG = "$env:USERPROFILE\.kube\circleguard-dev"
$env:USE_GKE_GCLOUD_AUTH_PLUGIN = "True"
& "$env:USERPROFILE\istio\istioctl.exe" dashboard kiali
```
(Requires dev cluster scaled up to 1 node first)

## Next Action

Phase 3.11 — Kiali screenshot (scale dev to 1 node, open browser, save screenshot to docs/diagrams/kiali-graph.png).
Then Phase 4 — CI/CD pipeline updates.
