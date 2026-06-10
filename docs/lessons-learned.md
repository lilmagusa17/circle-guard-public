# Lessons Learned — CircleGuard Proyecto Final SE5

A candid record of what worked well, what didn't, and what we would do differently in a future project.

---

## What Worked Well

### 1. Choosing Istio Early (Phase 3 before Phase 4–10)

Istio was deliberately placed in Phase 3 rather than at the end. This single decision gave us:
- mTLS STRICT across all namespaces (satisfying the security TLS requirement)
- Circuit breaker and retry (satisfying the resilience design pattern requirement)
- Sidecar pattern (satisfying the third design pattern requirement)
- Kiali + Jaeger + Prometheus from `istio/samples/addons` (jumpstarting observability)
- Canary traffic splits for the CI/CD advanced requirement

If Istio had been added last, all deployments would have needed re-tuning. Adding it first meant everything else was built on a stable mesh foundation.

### 2. Conventional Commits Automation

Enforcing Conventional Commits format (`feat:`, `fix:`, `chore:`, `BREAKING CHANGE`) meant that `ci/semver.sh` could automatically determine version bumps, and `ci/release-notes.sh` could group changes without manual categorization. After the initial setup cost, every release was fully automated.

### 3. Terraform Modules

Modular Terraform (`modules/vpc`, `modules/gke`, `modules/iam`, etc.) made it straightforward to create three environments with different sizing by simply changing input variables. The pattern also made it easy to understand what was provisioned without reading hundreds of lines of HCL.

### 4. Session Start/Stop Scripts

`ci/session-start.sh` and `ci/session-stop.sh` automated cluster scaling and infrastructure startup. Without these, GCP quota management would have been a daily manual chore.

### 5. External Secrets Operator

ESO eliminated the risk of accidentally committing credentials. Once configured, adding a new secret to GCP Secret Manager and referencing it via `ExternalSecret` was a clean, repeatable workflow.

---

## What Didn't Work Well

### 6. Istio Sidecar Probe Rewriting (Biggest Time Sink)

Istio's CNI agent rewrites container health probes to route through `pilot-agent`. This converts `tcpSocket` probes into HTTP probes under the hood. For services without `/actuator/health` (all 8 CircleGuard services), this caused readiness failures and restart loops on first deployment.

**What we'd do differently**: Add `spring-boot-starter-actuator` to all services from the start, or configure `httpGet` probes on `/actuator/health` from day one. The tcpSocket workaround works but creates confusion during debugging.

### 7. Postgres `docker-entrypoint-initdb.d` Init Scripts

The PostgreSQL image only runs init scripts on a fresh empty data directory. If the pod restarted during initialization (which happens during cluster scheduling), the init scripts were skipped and application databases were never created. This caused silent failures that took hours to debug.

**What we'd do differently**: Use a Kubernetes `Job` with `CREATE DATABASE IF NOT EXISTS` that runs on every cluster start, not just first init. This is idempotent and restart-safe.

### 8. Kafka/Neo4j Kubernetes Service Env Var Injection

Kubernetes automatically injects `KAFKA_PORT=tcp://...` and `NEO4J_PORT_7687_TCP_PORT=...` env vars into all pods in the same namespace as Services named `kafka` and `neo4j`. Confluent Kafka's `dub` framework treats any `KAFKA_*` variable as a broker config key. Neo4j treats any `NEO4J_*` variable as a configuration override. Both images crashed immediately on deployment.

**Fix**: `enableServiceLinks: false` on the affected pods. This is now documented in the Known Issues section of CLAUDE.md.

**What we'd do differently**: Add `enableServiceLinks: false` to all infrastructure pods by default as a precaution.

### 9. GCP Quota Surprises

Multiple quota limits (CPUS_ALL_REGIONS=12, IN_USE_ADDRESSES=8, SSD_TOTAL_GB=250) caused cascading failures during the initial provisioning phase. Clusters entered ERROR state when quota was exceeded mid-creation.

**What we'd do differently**: Request quota increases at the beginning of the project, not after hitting limits. Also add explicit quota checks to `ci/session-start.sh` before provisioning.

### 10. Jenkins DooD (Docker outside of Docker) Complexity

Jenkins running as a Docker container, using the host Docker socket, with Testcontainers starting containers on the host, reached via `host.docker.internal`... this chain of indirection caused a week of debugging. The API version mismatch between the Jenkins Docker library (v1.32) and Docker 29.x (minimum v1.44) required a custom proxy script.

**What we'd do differently**: Use `docker:dind` (Docker-in-Docker) as a Jenkins agent rather than socket mounting. It's more isolated, has no API version mismatch issues, and doesn't require socket permission hacks.

### 11. Missing Spring Boot Actuator in All Services

None of the 8 CircleGuard services included `spring-boot-starter-actuator`. This meant no `/actuator/health` endpoints for Kubernetes readiness/liveness probes, no `/actuator/prometheus` for Prometheus scraping, and no structured health status. The tcpSocket probe workaround works but is a weaker signal than HTTP health probes.

**What we'd do differently**: Add actuator as a standard dependency from the initial service scaffold. The observability work in Phase 7 (ServiceMonitors, per-service dashboards) was significantly harder without Micrometer/Prometheus endpoints.

### 12. Single-Node Production Resource Exhaustion with Istio

After enabling Istio sidecar injection in production, each pod gained an Envoy container consuming ~100 MB RAM. With 8 services × 2 replicas × 2 containers = 32 containers on a 1-node e2-standard-2 (8 GB RAM), the node hit 96% memory and started OOM-killing pods.

**Fix**: Scale production services to `replicas: 1`. But the real fix is to add a second node to production.

**What we'd do differently**: Account for Istio sidecar overhead (~100 MB/pod) in the initial node sizing. For a service mesh deployment, `e2-standard-4` (16 GB) is a better production baseline than `e2-standard-2`.

---

## Key Decisions We Would Repeat

- **GitHub Flow over GitFlow**: Simple, fast, and the branching strategy enforced in Jenkinsfiles matched it naturally.
- **GKE over DigitalOcean**: Terraform module quality, GCP Secret Manager integration, and Workload Identity made the credential management story much cleaner.
- **Terraform with remote GCS state**: The state locking prevented multiple developers from applying simultaneously. Saved us from state corruption.
- **Separate infra pods from app pods in manifests**: Having `dev-infrastructure.yaml` separate from per-service deployment files made it easy to restart just the app pods without touching the database pods.
