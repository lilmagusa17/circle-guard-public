# CircleGuard

**Absolute Privacy. High-Speed Containment. Secure Campus.**

CircleGuard is a university health-monitoring and contact-tracing platform. It identifies interconnected contact groups ("Circles") and applies rapid health fences while preserving individual anonymity through k-anonymity and cryptographic identity vaulting.

---

## What is CircleGuard?

CircleGuard runs 8 microservices on Google Kubernetes Engine (GKE) with full service-mesh encryption (Istio mTLS) and event-driven health status propagation via Apache Kafka.

### Key capabilities
- **Privacy-as-Code**: Zero real-name exposure outside a secure Health Center identity vault (FERPA-compliant).
- **Recursive Containment**: Health status cascades (Suspect → Probable → Confirmed) trigger notifications in milliseconds using Neo4j graph traversals.
- **Campus Integration**: Smart check-ins via QR-code campus entry validation backed by Redis session cache.
- **Geospatial Analytics**: k-anonymity-filtered hotspot dashboard for Health Center staff.

---

## Architecture Overview

Eight Spring Boot 3.2.x microservices communicate over Istio mTLS (STRICT mode) with Kafka for async events.

| Service | Port | Responsibility |
|---------|------|---------------|
| `gateway-service` | 8087 | API gateway, QR validation, JWT routing, Redis sessions |
| `auth-service` | 8180 | JWT authentication, LDAP integration |
| `identity-service` | 8083 | Cryptographic identity vault |
| `form-service` | 8086 | Health survey forms, Kafka producer |
| `notification-service` | 8082 | Email/push notifications, Kafka consumer |
| `promotion-service` | 8088 | Health status lifecycle, Neo4j graph, Redis cache |
| `dashboard-service` | 8084 | Geospatial hotspot analytics (k-anonymity) |
| `file-service` | 8085 | Certificate and document storage (S3-compatible) |

**Infrastructure**: PostgreSQL 16, Apache Kafka (Confluent 7.6), Redis 7.2, Neo4j 5.26

**Deployment**: GKE clusters (`circleguard-dev`, `circleguard-stage`, `circleguard-prod`) in `us-central1` with Istio 1.24.3, External Secrets Operator, and cert-manager.

Full diagrams: [`docs/diagrams/system-overview.md`](docs/diagrams/system-overview.md) | [`docs/diagrams/deployment-view.md`](docs/diagrams/deployment-view.md)

---

## Provisioning Infrastructure

Requires: `terraform >= 1.6`, `gcloud`, `kubectl >= 1.28`, `helm >= 3.13`.

```bash
# 1. Authenticate with GCP
gcloud auth login
gcloud config set project tallerfinal-496702

# 2. Enable required APIs (first time only)
gcloud services enable container.googleapis.com compute.googleapis.com \
    artifactregistry.googleapis.com secretmanager.googleapis.com \
    iam.googleapis.com iamcredentials.googleapis.com \
    cloudresourcemanager.googleapis.com --project=tallerfinal-496702

# 3. Apply dev environment
cd terraform/envs/dev
terraform init
terraform apply -auto-approve

# 4. Get cluster credentials
gcloud container clusters get-credentials circleguard-dev \
    --region us-central1 --project tallerfinal-496702
```

Full provisioning guide: [`terraform/README.md`](terraform/README.md)

> Important: GCP quota `CPUS_ALL_REGIONS=12`. Only one cluster should have active nodes at a time. Scale others to 0 before scaling a cluster up.

---

## Deploying Services

After provisioning and getting cluster credentials:

```bash
# Create namespaces
kubectl apply -f k8s/namespaces.yaml

# Deploy infrastructure (Kafka, PostgreSQL, Redis, Neo4j)
kubectl apply -f k8s/infra/dev-infrastructure.yaml

# Wait for infra pods to be Running
kubectl get pods -n circleguard-dev -w

# Apply RBAC
kubectl apply -f k8s/rbac/dev/rbac.yaml

# Deploy all 8 services
kubectl apply -f k8s/dev/

# Apply Istio resources
kubectl apply -f k8s/istio/dev/

# Apply External Secrets Operator resources
kubectl apply -f k8s/eso/dev/

# Verify all pods are Running
kubectl get pods -n circleguard-dev
```

For production deployment, use the Jenkins master pipeline which runs the full CI/CD flow with SonarQube, Trivy, and canary deployment.

---

## Local Development

### Prerequisites

- Java 21, Docker Desktop
- `./gradlew` wrapper (no local Gradle needed)

### Build

```bash
# Build all services
./gradlew build

# Build a single service
./gradlew :services:circleguard-auth-service:build

# Build Docker JAR only (no tests)
./gradlew bootJar
```

### Run locally

```bash
# Start local infrastructure
docker-compose -f docker-compose.dev.yml up -d

# Run all services in parallel
./gradlew bootRun --parallel

# Run a specific service
./gradlew :services:circleguard-auth-service:bootRun
```

### API Documentation

Each service exposes OpenAPI 3.0 when running locally:
```
http://localhost:<port>/swagger-ui/index.html
```

---

## Running Tests

```bash
# All unit tests (all 8 services)
./gradlew test

# Single service tests
./gradlew :services:circleguard-auth-service:test

# Integration tests (requires Docker for Testcontainers)
./gradlew test -Pintegration

# Generate JaCoCo coverage report
./gradlew test jacocoTestReport
# Report: build/reports/jacoco/test/html/index.html
```

### Performance Tests (Locust)

```bash
cd tests/performance
pip install locust
locust -f locustfile.py --host=http://<ingress-ip>
# Open http://localhost:8089 to configure and start the load test
```

### Security Tests (OWASP ZAP)

```bash
# Run ZAP baseline scan against dev environment
bash tests/security/zap-baseline.sh
# Report saved to tests/security/zap-report.html
```

---

## Accessing Dashboards

All dashboards are accessed via `kubectl port-forward` or `istioctl dashboard`.

```bash
# Kiali — service mesh topology and traffic
istioctl dashboard kiali -n istio-system

# Grafana — metrics and dashboards
kubectl port-forward -n monitoring svc/grafana 3000:3000
# Open: http://localhost:3000 (admin/prom-operator)

# Jaeger — distributed tracing
istioctl dashboard jaeger

# Kibana — log search
kubectl port-forward -n logging svc/kibana-kibana 5601:5601
# Open: http://localhost:5601

# Prometheus — raw metrics
kubectl port-forward -n monitoring svc/prometheus-operated 9090:9090
# Open: http://localhost:9090

# Jenkins — CI/CD pipelines
# Jenkins runs as a local Docker container on the developer's machine
# Open: http://localhost:8080
```

---

## Key Documentation

| Document | Description |
|----------|-------------|
| [`terraform/README.md`](terraform/README.md) | Terraform module layout, provisioning guide, destroy commands |
| [`docs/operations/README.md`](docs/operations/README.md) | Operations manual index |
| [`docs/patterns/README.md`](docs/patterns/README.md) | Design patterns catalog |
| [`docs/diagrams/system-overview.md`](docs/diagrams/system-overview.md) | System architecture diagram |
| [`docs/diagrams/deployment-view.md`](docs/diagrams/deployment-view.md) | GKE deployment topology |
| [`docs/diagrams/data-flow.md`](docs/diagrams/data-flow.md) | Key request/event flows |
| [`docs/diagrams/istio-mesh.md`](docs/diagrams/istio-mesh.md) | Istio mesh topology |
| [`docs/operations/change-management.md`](docs/operations/change-management.md) | CM process, approval gates |
| [`docs/operations/rollback.md`](docs/operations/rollback.md) | Rollback commands and drill results |
| [`docs/operations/security.md`](docs/operations/security.md) | Threat model and security posture |
| [`docs/operations/versioning.md`](docs/operations/versioning.md) | SemVer convention and release process |

---

## CI/CD Pipelines

Three Jenkins pipelines (run in local Jenkins Docker container):

| Pipeline | Jenkinsfile | Trigger | Deploys To |
|----------|-------------|---------|-----------|
| DEV | `ci/Jenkinsfile.dev` | Branch push | `circleguard-dev` |
| STAGE | `ci/Jenkinsfile.stage` | Manual | `circleguard-stage` |
| MASTER | `ci/Jenkinsfile.master` | Manual / tag | `circleguard-production` (with canary) |

All pipelines include: Build → SonarQube → Unit Tests → Integration Tests → Docker Build + Trivy scan → Push → Deploy.

The master pipeline additionally runs: E2E Tests → Canary (10%, 30 min) → Prod Approval → Full Deploy → Semantic Version Tag → GitHub Release.

---

## Success Metrics

| Metric | Target |
|--------|--------|
| Containment Speed | < 60 seconds from survey submission to fence notification |
| Privacy Compliance | 100% — zero real names in the contact graph |
| System Uptime | 99.5% during academic peak hours (07:00–22:00) |
| False Positive Rate | < 15% |
