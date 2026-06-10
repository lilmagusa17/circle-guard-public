# CircleGuard Deployment View

GKE clusters across three environments with node counts, namespaces, and components.

---

```mermaid
---
title: CircleGuard Deployment Architecture (GKE — us-central1)
---
graph TB
    subgraph GCP["Google Cloud Platform — project: tallerfinal-496702"]
        subgraph DEV["circleguard-dev cluster  (e2-standard-2 × 1-3 nodes)"]
            subgraph NS_DEV["namespace: circleguard-dev"]
                direction TB
                SVC_DEV["8 microservices (replicas: 1 each)"]
                INFRA_DEV["5 infra pods: postgres, kafka, zookeeper, redis, neo4j"]
                ISTIO_DEV["Istio sidecars (Envoy) — mTLS STRICT"]
                ESO_DEV["ExternalSecret resources → GCP Secret Manager"]
            end
            subgraph ISTIO_SYS_DEV["namespace: istio-system"]
                CTRL_DEV["istiod, ingressgateway"]
                OBS_DEV["kiali, jaeger, prometheus, grafana"]
            end
        end

        subgraph STAGE["circleguard-stage cluster  (e2-standard-2 × 1-3 nodes)"]
            subgraph NS_STAGE["namespace: circleguard-stage"]
                direction TB
                SVC_STAGE["8 microservices (replicas: 1 each)"]
                INFRA_STAGE["5 infra pods: postgres, kafka, zookeeper, redis, neo4j"]
                ISTIO_STAGE["Istio sidecars (Envoy) — mTLS STRICT"]
            end
            subgraph ISTIO_SYS_STAGE["namespace: istio-system"]
                CTRL_STAGE["istiod, ingressgateway"]
            end
        end

        subgraph PROD["circleguard-prod cluster  (e2-standard-2 × 1 node)"]
            subgraph NS_PROD["namespace: circleguard-production"]
                direction TB
                SVC_PROD["8 microservices (replicas: 1 each)"]
                INFRA_PROD["5 infra pods: postgres, kafka, zookeeper, redis, neo4j"]
                ISTIO_PROD["Istio sidecars (Envoy) — mTLS STRICT"]
                TRIVY_PROD["trivy-daily-scan CronJob"]
            end
            subgraph ISTIO_SYS_PROD["namespace: istio-system"]
                CTRL_PROD["istiod, ingressgateway"]
                IP_PROD["External IP: 34.58.31.128"]
            end
            subgraph CERT["namespace: cert-manager"]
                CM["cert-manager v1.14.4"]
                LE["Let's Encrypt ClusterIssuer"]
            end
        end

        subgraph SHARED["Shared GCP Resources"]
            SM["Secret Manager — DB passwords, JWT secret, Docker Hub creds"]
            GCS["GCS Bucket — Terraform remote state (circleguard-tfstate-*)"]
            AR["Artifact Registry — circleguard Docker repo"]
            JENKINS["Jenkins (local Docker) — CI/CD orchestration"]
        end
    end

    JENKINS -->|"kubectl apply"| NS_DEV
    JENKINS -->|"kubectl apply"| NS_STAGE
    JENKINS -->|"kubectl apply"| NS_PROD
    SM -->|"External Secrets Operator sync"| NS_DEV
    SM -->|"External Secrets Operator sync"| NS_STAGE
    SM -->|"External Secrets Operator sync"| NS_PROD
    GCS -->|"Terraform state"| DEV
    GCS -->|"Terraform state"| STAGE
    GCS -->|"Terraform state"| PROD
```

---

## Environment Sizing

| Environment | Cluster Name | Node Type | Nodes | Purpose |
|-------------|-------------|-----------|-------|---------|
| Dev | `circleguard-dev` | e2-standard-2 (2 vCPU, 8 GB) | 1–3 (autoscale 0–3) | Development, CI pipeline testing |
| Stage | `circleguard-stage` | e2-standard-2 | 1–3 (autoscale 0–3) | Pre-production validation, ZAP scans |
| Production | `circleguard-prod` | e2-standard-2 | 1 (autoscale 0–3) | Live traffic |

All clusters are in region `us-central1`. Clusters are scaled to 0 nodes between sessions to stay within the `CPUS_ALL_REGIONS=12` quota and minimize costs.

---

## Network Topology

```
Internet
    |
    | HTTPS :443 / HTTP :80
    ↓
GCP External Load Balancer (IP: 34.58.31.128)
    |
    ↓
Istio Ingress Gateway (istio-system namespace)
    |
    | mTLS
    ↓
Application Pods (circleguard-production namespace)
    |
    | mTLS (all service-to-service)
    ↓
Infrastructure Pods (PostgreSQL, Kafka, Redis, Neo4j)
    (no Istio sidecar — enableServiceLinks: false)
```
