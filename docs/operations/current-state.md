# CircleGuard — Current Deployed State

Live record of what is actually deployed. Update this file every time infrastructure changes.

**Last updated:** 2026-06-04

---

## GCP Project

- **Project ID:** `circleguard-final`
- **Region:** `us-central1`
- **Billing account:** `019044-EE5C1C-F61E8F`

## Terraform State Bucket

- **Bucket:** `gs://circle-guard-tfstate-final`
- **Status:** ✅ Created, versioning enabled

## GKE Clusters

| Cluster | Status | Nodes | Notes |
|---------|--------|-------|-------|
| `circleguard-dev` | ✅ Running | 0 (scaled down) | Apply complete; scale up before use |
| `circleguard-stage` | ✅ Running | 0 (scaled down) | Apply complete; scale up before use |
| `circleguard-prod` | ✅ Running | 0 (scaled down) | Apply complete; scale up before use |

## kubeconfig Files

| File | Cluster | Status |
|------|---------|--------|
| `~/.kube/circleguard-dev` | circleguard-dev | ✅ Generated |
| `~/.kube/circleguard-stage` | circleguard-stage | ✅ Generated |
| `~/.kube/circleguard-prod` | circleguard-prod | ✅ Generated |

Use with: `$env:KUBECONFIG = "$env:USERPROFILE\.kube\circleguard-dev"; $env:USE_GKE_GCLOUD_AUTH_PLUGIN = "True"`
Also need gcloud on PATH: `$env:PATH = "C:\Users\Mariana\AppData\Local\Google\Cloud SDK\google-cloud-sdk\bin;$env:PATH"`

## Artifact Registry

- **URL:** `us-central1-docker.pkg.dev/circleguard-final/circleguard`
- **Status:** ✅ Created (in dev env)

## Secret Manager Secrets (shells only — no values set yet)

| Env | Secrets |
|-----|---------|
| dev | cg-db-password-dev, cg-jwt-secret-dev, cg-dockerhub-user-dev, cg-dockerhub-password-dev, cg-mail-password-dev |
| stage | same pattern with -stage suffix |
| prod | same pattern with -prod suffix |

## IAM Service Accounts

Per env: `eso-sa-<env>` (ESO, secretmanager.secretAccessor) + `cg-{auth,dashboard,file,form,gateway,identity,notification,promotion}-<env>` (Workload Identity).

## Phase Completion

| Phase | Status | Notes |
|-------|--------|-------|
| Phase 0 — Foundation | 🔴 Not started | GCP access + APIs now done manually; board/docs pending |
| Phase 1 — Terraform | 🟢 Done | All 16 tasks complete |
| Phase 2 — K8s Migration | 🔴 Not started | Needs Phase 1 ✅ |
| Phase 3 — Istio | 🔴 Not started | Needs Phase 2 |
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
```

## Scale Down Commands (after working)

```powershell
& $gcloud container clusters resize circleguard-dev --node-pool=default-pool --num-nodes=0 --region=us-central1 --project=circleguard-final --quiet
& $gcloud container clusters resize circleguard-stage --node-pool=default-pool --num-nodes=0 --region=us-central1 --project=circleguard-final --quiet
& $gcloud container clusters resize circleguard-prod --node-pool=default-pool --num-nodes=0 --region=us-central1 --project=circleguard-final --quiet
```

## Next Action

Phase 2 — K8s Migration. Scale dev cluster up, then:
1. Inventory existing `k8s/` manifests
2. Update StorageClass from `do-block-storage` → `standard-rwo`
3. Remove DO-specific LoadBalancer annotations
4. Deploy infrastructure (Kafka, Postgres, Redis, Neo4j) to dev namespace
