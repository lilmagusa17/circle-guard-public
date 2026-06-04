# Infrastructure Architecture

CircleGuard GCP infrastructure — three environments on GKE.

```mermaid
graph TB
    subgraph GCP["Google Cloud Platform — project: circleguard-final"]
        subgraph AR["Artifact Registry (us-central1)"]
            REPO["circleguard Docker repo"]
        end

        subgraph SM["Secret Manager"]
            SEC_DEV["cg-*-dev secrets"]
            SEC_STAGE["cg-*-stage secrets"]
            SEC_PROD["cg-*-prod secrets"]
        end

        subgraph DEV["VPC: circleguard-dev (10.10.0.0/24)"]
            GKE_DEV["GKE: circleguard-dev\n1 node/zone · Spot e2-standard-2\nautoscale 0–3"]
            subgraph NS_DEV["Namespace: circleguard-dev"]
                SVC_DEV["8 microservices\n+ Kafka + Postgres\n+ Redis + Neo4j"]
            end
            LB_DEV["External LoadBalancer\n(GCP External IP)"]
            GKE_DEV --> NS_DEV
            LB_DEV --> GKE_DEV
        end

        subgraph STAGE["VPC: circleguard-stage (10.20.0.0/24)"]
            GKE_STAGE["GKE: circleguard-stage\n1 node/zone · Spot e2-standard-2\nautoscale 0–3"]
            subgraph NS_STAGE["Namespace: circleguard-stage"]
                SVC_STAGE["8 microservices\n+ infrastructure"]
            end
            LB_STAGE["External LoadBalancer"]
            GKE_STAGE --> NS_STAGE
            LB_STAGE --> GKE_STAGE
        end

        subgraph PROD["VPC: circleguard-prod (10.30.0.0/24)"]
            GKE_PROD["GKE: circleguard-prod\n1 node/zone · e2-standard-2\nautoscale 0–5"]
            subgraph NS_PROD["Namespace: circleguard-production"]
                SVC_PROD["8 microservices\n+ infrastructure"]
            end
            LB_PROD["External LoadBalancer"]
            GKE_PROD --> NS_PROD
            LB_PROD --> GKE_PROD
        end

        subgraph GCS["GCS: circle-guard-tfstate-final"]
            TF_DEV["envs/dev"]
            TF_STAGE["envs/stage"]
            TF_PROD["envs/prod"]
        end
    end

    USER["Users / Jenkins"] --> LB_DEV
    USER --> LB_STAGE
    USER --> LB_PROD
    NS_DEV -- "Workload Identity" --> SM
    NS_STAGE -- "Workload Identity" --> SM
    NS_PROD -- "Workload Identity" --> SM
    NS_DEV --> REPO
    NS_STAGE --> REPO
    NS_PROD --> REPO
```

## Network addressing

| Env   | Nodes           | Pods            | Services        |
|-------|-----------------|-----------------|-----------------|
| dev   | 10.10.0.0/24    | 10.10.4.0/22    | 10.10.8.0/24    |
| stage | 10.20.0.0/24    | 10.20.4.0/22    | 10.20.8.0/24    |
| prod  | 10.30.0.0/24    | 10.30.4.0/22    | 10.30.8.0/24    |

## Terraform state

All state in `gs://circle-guard-tfstate-final/` with per-env prefix (`envs/dev`, `envs/stage`, `envs/prod`).

## IAM / Workload Identity

Each namespace has:
- One ESO service account (`eso-sa-<env>`) with `secretmanager.secretAccessor`
- Eight per-service GSAs (`cg-<service>-<env>`) for future fine-grained access
- Workload Identity bindings so KSAs can impersonate GSAs without key files
