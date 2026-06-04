# CircleGuard — Current Deployed State

Live record of what is actually deployed. Update this file every time infrastructure changes.

**Last updated:** 2026-06-04

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
| `circleguard-dev` | ✅ Running | 0 (scaled down) | All 13 pods verified |
| `circleguard-stage` | ✅ Running | 0 (scaled down) | All 13 pods verified |
| `circleguard-prod` | ✅ Running | 0 (scaled down) | All 13 pods verified |

## Docker Hub Images (published)

All 8 images at `magusa17/circleguard-{auth,dashboard,file,form,gateway,identity,notification,promotion}:latest`
Built and pushed 2026-06-04.

## Kubernetes Namespaces

| Namespace | Status |
|-----------|--------|
| `circleguard-dev` | ✅ Created, all pods verified |
| `circleguard-stage` | ✅ Created, all pods verified |
| `circleguard-production` | ✅ Created, all pods verified |

## Infrastructure per namespace

Deployed: Postgres (with DB init), Zookeeper, Kafka, Redis, Neo4j  
Databases: `circleguard_{auth,dashboard,form,promotion,identity}` created in each env

## Phase Completion

| Phase | Status | Notes |
|-------|--------|-------|
| Phase 0 — Foundation | 🔴 Not started | GCP access done manually; board/docs pending |
| Phase 1 — Terraform | 🟢 Done | All 16 tasks complete |
| Phase 2 — K8s Migration | 🟢 Done | All 12 tasks complete |
| Phase 3 — Istio | 🔴 Not started | Needs Phase 2 ✅ |
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
**Production has a DB init Job** that runs automatically — no manual step needed.

### Service startup time
Spring Boot services take 75-90s to start on Spot VMs. Do not check pod readiness before 2 minutes after deploy.

### Production pod scheduling
All pods may land on one node if only one node was ready at deploy time. If OOM kills occur:
1. `kubectl cordon <overloaded-node>`
2. Delete service pods: `kubectl delete pods -n circleguard-production -l app=<svc>`
3. Let Kubernetes reschedule to other nodes
4. `kubectl uncordon <node>`

## Next Action

Phase 3 — Istio Service Mesh.
1. Scale dev: `--num-nodes=1`
2. `istioctl install --set profile=demo -y`
3. Label namespace: `kubectl label namespace circleguard-dev istio-injection=enabled`
