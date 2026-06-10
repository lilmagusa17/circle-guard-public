# GCP Cost Analysis — CircleGuard

Last updated: 2026-06-09
GCP Project: `tallerfinal-496702` (region: `us-central1`)

---

## Monthly Cost Estimates by Environment

All clusters use `e2-standard-2` nodes (2 vCPU, 8 GB RAM). Pricing based on GCP us-central1 on-demand rates.

### e2-standard-2 Pricing Reference

| Mode | Cost |
|------|------|
| On-demand | ~$0.067/hour ≈ $48.92/node/month |
| Spot VM | ~$0.020/hour ≈ $14.60/node/month (70% cheaper) |

---

### Dev Environment (`circleguard-dev`)

Regional cluster, 1 node per zone (up to 3 zones = 3 nodes max when fully scaled up).

| Resource | Configuration | Monthly Cost (if always on) |
|----------|--------------|----------------------------|
| GKE node(s) | 1–3 × e2-standard-2 | $48.92–$146.76 |
| GKE cluster fee | $0.10/hour (zonal: free) | ~$73 (regional) |
| External IP | 1 × static IP | $7.20 |
| Boot disks | 3 × 30 GB pd-standard | ~$3.60 |
| **Subtotal (always on)** | | **~$132–$230/month** |
| **Actual (scale to 0 between sessions)** | ~4h/day active | **~$15–20/month** |

### Stage Environment (`circleguard-stage`)

Same sizing as dev. Typically only activated for pipeline stage deployments (a few hours per week).

| Resource | Configuration | Monthly Cost |
|----------|--------------|-------------|
| GKE nodes | 1–3 × e2-standard-2 | $48.92–$146.76 |
| Cluster fee | regional | ~$73 |
| External IP | 1 | $7.20 |
| **Actual (scaled down most of the time)** | | **~$10–15/month** |

### Production Environment (`circleguard-prod`)

Single node (1 zone) to minimize costs while keeping a live cluster.

| Resource | Configuration | Monthly Cost |
|----------|--------------|-------------|
| GKE node | 1 × e2-standard-2 | $48.92 |
| Cluster fee | zonal: free | $0 |
| External IP (ingress) | 1 × static | $7.20 |
| Boot disk | 1 × 30 GB pd-standard | ~$1.20 |
| **Subtotal (always on)** | | **~$57/month** |
| **Actual (scale to 0 overnight)** | ~12h/day | **~$15–20/month** |

---

## Shared Resource Costs

| Resource | Details | Monthly Cost |
|----------|---------|-------------|
| GCS bucket (Terraform state) | < 1 GB, versioning on | < $0.05 |
| Artifact Registry | Docker images for 8 services (~50 GB) | ~$5.00 |
| GCP Secret Manager | < 10 secrets, < 1M accesses/month | < $1.00 |
| Networking egress | < 1 GB/month (mostly intra-region) | < $0.10 |
| Cloud Logging | Default quota (50 GB/month free tier) | $0 |
| Cloud Monitoring | Default quota (free tier) | $0 |

---

## Total Monthly Cost Estimate

| Scenario | Monthly Cost |
|----------|-------------|
| All clusters always on (worst case) | ~$420–$570/month |
| All clusters at 0 nodes, just paying for cluster + IPs | ~$25/month |
| **Typical (active sessions, scale-to-0 strategy)** | **~$50–65/month** |

---

## Cost Reduction Recommendations

### 1. Use Spot/Preemptible VMs (Highest Impact)

Switching from on-demand to Spot VMs for dev and stage would reduce compute costs by ~70%.

```hcl
# In terraform/modules/gke/main.tf, add to node_config:
spot = true
```

Risk: Spot VMs can be preempted with 30s notice. Acceptable for dev/stage; not recommended for production.
Savings: ~$70/month on dev+stage if always on.

### 2. Scale Clusters to 0 Between Sessions (Already Implemented)

`ci/session-stop.sh` scales all clusters to 0 nodes. This is the current primary cost-saving measure.

```bash
# Scale all clusters to 0
gcloud container clusters resize circleguard-dev --node-pool=default-pool --num-nodes=0 \
    --region=us-central1 --project=tallerfinal-496702 --quiet
gcloud container clusters resize circleguard-stage --node-pool=default-pool --num-nodes=0 \
    --region=us-central1 --project=tallerfinal-496702 --quiet
gcloud container clusters resize circleguard-prod --node-pool=default-pool --num-nodes=0 \
    --region=us-central1 --project=tallerfinal-496702 --quiet
```

### 3. Use e2-small for Dev (If Resource Allows)

Istio sidecar injection requires ~100 MB RAM per pod. With 13 pods in dev (8 services + 5 infra), minimum RAM needed is ~3 GB. e2-small (2 GB) is insufficient; e2-medium (4 GB) may work but is tight.

Recommendation: Stay with e2-standard-2 for dev to avoid OOM kills. Evaluate e2-medium if pod count stays below 10.

### 4. Destroy Stage and Prod Clusters During Extended Absences

For absences longer than a weekend:
```bash
cd terraform/envs/stage && terraform destroy -auto-approve
cd terraform/envs/prod && terraform destroy -auto-approve
```
Savings: Eliminates cluster fee and disk costs during idle periods.
Note: Takes ~10 minutes to reprovision from scratch.

### 5. Consolidate Dev and Stage Into One Cluster (Namespaces)

Use separate namespaces (`circleguard-dev`, `circleguard-stage`) within a single cluster rather than two separate clusters. This saves one full cluster fee and one external IP.

Not implemented currently due to risk of cross-namespace interference. Would save ~$15–25/month.

---

## Quota Constraints

| Quota | Limit | Impact |
|-------|-------|--------|
| `CPUS_ALL_REGIONS` | 12 vCPUs | Limits to 2 active nodes across all clusters (2 vCPU each) |
| `IN_USE_ADDRESSES` | 8 IPs in us-central1 | Limits total regional IPs; avoid > 3 active clusters simultaneously |
| `SSD_TOTAL_GB` | 250 GB | Use pd-standard disks only; avoid pd-balanced |

These quotas are enforced by the session management scripts to prevent overage errors.
