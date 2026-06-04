# CircleGuard — Terraform Infrastructure

Provisions GKE clusters and supporting GCP resources for dev, stage, and prod environments.

## Prerequisites

- `terraform >= 1.6` on PATH
- `gcloud` authenticated: `gcloud auth login && gcloud auth application-default login`
- GCP project: `circleguard-final`
- Terraform SA key at `~/.gcp/terraform-key.json` (for CI; local uses ADC)

## First-time setup — create the state bucket manually

Before running `terraform init` for the first time, create the GCS bucket for remote state:

```bash
gcloud config set project circleguard-final
gsutil mb -l us-central1 gs://circle-guard-tfstate-final
gsutil versioning set on gs://circle-guard-tfstate-final
```

This only needs to be done once. All three envs share the same bucket with different prefixes.

## Module layout

```
terraform/
  modules/
    vpc/              VPC network, subnet with secondary IP ranges, firewall rules
    gke/              GKE cluster (regional), node pool with autoscaling + Spot option
    artifact_registry/ Docker repository in Artifact Registry
    secrets/          Secret Manager secret containers (no values — only shells)
    iam/              Service accounts for microservices, Jenkins, ESO + WI bindings
  envs/
    dev/              1 node/zone, Spot e2-standard-2, autoscale 0–3
    stage/            1 node/zone, Spot e2-standard-2, autoscale 0–3
    prod/             1 node/zone, regular e2-standard-2, autoscale 0–5
```

## Remote state

All state stored in GCS: `gs://circle-guard-tfstate-final/`

| Env   | State prefix   |
|-------|----------------|
| dev   | `envs/dev`     |
| stage | `envs/stage`   |
| prod  | `envs/prod`    |

## Provision a new environment (from scratch)

```bash
# 1. Authenticate
gcloud auth login
gcloud auth application-default login
gcloud config set project circleguard-final

# 2. Init and apply
cd terraform/envs/<env>
terraform init
terraform apply

# 3. Update kubeconfig
gcloud container clusters get-credentials circleguard-<env> \
  --region us-central1 --project circleguard-final
```

## Destroy an environment (to save costs)

```bash
cd terraform/envs/<env>
terraform destroy
```

> `deletion_protection = false` is set explicitly in the GKE module so destroy works without manual intervention.

## ⚠️ Quota limits — IMPORTANT

GCP free tier has `CPUS_ALL_REGIONS = 12`. Each GKE cluster with 1 node/zone × 3 zones = 6 vCPUs.
**Never run more than one cluster with nodes at the same time.**

Before applying a new env, scale the others to 0:
```bash
gcloud container clusters resize circleguard-dev \
  --node-pool=default-pool --num-nodes=0 \
  --region=us-central1 --project=circleguard-final --quiet
```

## Network addressing

| Env   | Nodes           | Pods            | Services        |
|-------|-----------------|-----------------|-----------------|
| dev   | 10.10.0.0/24    | 10.10.4.0/22    | 10.10.8.0/24    |
| stage | 10.20.0.0/24    | 10.20.4.0/22    | 10.20.8.0/24    |
| prod  | 10.30.0.0/24    | 10.30.4.0/22    | 10.30.8.0/24    |

## Key variables per env

| Variable     | dev           | stage         | prod          |
|--------------|---------------|---------------|---------------|
| machine_type | e2-standard-2 | e2-standard-2 | e2-standard-2 |
| use_spot     | true          | true          | false         |
| node_count   | 1/zone        | 1/zone        | 1/zone        |
| min_nodes    | 0/zone        | 0/zone        | 0/zone        |
| max_nodes    | 3/zone        | 3/zone        | 5/zone        |

## Artifact Registry

Created once in `envs/dev`. URL: `us-central1-docker.pkg.dev/circleguard-final/circleguard`

Stage and prod do not re-create it — they reuse the same registry.
