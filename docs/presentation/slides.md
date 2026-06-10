# Presentation Slides — CircleGuard Proyecto Final SE5

Structure for the 20-slide final presentation. Each section maps to a phase in the implementation plan.

---

## Slide 1 — Title

**CircleGuard**
*University Health Monitoring & Contact Tracing Platform*

SE5 — Software Engineering 5 | Semestre 8 | Proyecto Final
Repository: github.com/lilmagusa17/circle-guard-public

---

## Slide 2 — Team & Stack

**Team Members**: [list team members]

**Core Technologies**:

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 3.2.4, Java 21 |
| Messaging | Apache Kafka 7.6 |
| Databases | PostgreSQL 16, Neo4j 5.26, Redis 7.2 |
| Containers | Docker, GKE (Google Kubernetes Engine) |
| Service Mesh | Istio 1.24.3 |
| CI/CD | Jenkins, SonarQube, Trivy |
| Infrastructure | Terraform, GCP Secret Manager, External Secrets Operator |
| Observability | Prometheus, Grafana, ELK Stack, Jaeger, Kiali |

---

## Slide 3 — Architecture Overview

*[Embed Mermaid diagram from docs/diagrams/system-overview.md]*

8 microservices, all communicating over Istio mTLS:
- **Entry**: Istio Ingress Gateway → gateway-service
- **Auth**: auth-service (JWT + LDAP)
- **Core logic**: promotion-service (Neo4j contact graph + Redis cache)
- **Events**: form-service → Kafka → notification-service
- **Analytics**: dashboard-service (k-anonymity filtered)
- **Storage**: file-service (S3-compatible certs), identity-service (FERPA vault)

---

## Slide 4 — Phase Completion Status

| Phase | Description | Status | Grade Weight |
|-------|-------------|--------|-------------|
| 0 | Foundation Setup | Partial | — |
| 1 | Terraform Infrastructure | Complete | 20% |
| 2 | K8s Migration to GKE | Complete | — |
| 3 | Service Mesh (Istio) BONUS | Complete | Bonus |
| 4 | CI/CD Avanzado | Complete | 15% |
| 5 | Design Patterns | Complete | 10% |
| 6 | Testing Enhancement | In Progress | 15% |
| 7 | Observability | In Progress | 10% |
| 8 | Security | Complete | 5% |
| 9 | Change Management | Complete | 5% |
| 10 | Documentation & Presentation | Complete | 10% |

---

## Slide 5 — Infrastructure (Phase 1)

**Terraform Modular Architecture**

```
terraform/
  modules/
    vpc/           → VPC + subnets + firewall rules
    gke/           → Regional GKE clusters with Workload Identity
    artifact_registry/ → Docker repo in us-central1
    secrets/       → GCP Secret Manager (DB passwords, JWT, Docker Hub)
    iam/           → Service accounts per service (least privilege)
  envs/dev/ stage/ prod/   → Module instantiation per env
```

**Three environments**: dev (1–3 nodes), stage (1–3 nodes), prod (1 node)
**Cost with scale-to-0 strategy**: ~$50–65/month total

*[Screenshot: gcloud container clusters list output]*

---

## Slide 6 — CI/CD Pipeline (Phase 4)

*[Diagram: pipeline stages flow]*

**Three Jenkinsfiles** (dev / stage / master):

```
Checkout → Build → SonarQube → Tests → Trivy → Docker Push → Deploy
```

**Master pipeline extras**:
- Integration + E2E tests
- Manual Prod Approval gate (30 min)
- Canary: 10% traffic for 30 min → second approval → 100%
- `ci/semver.sh`: auto-generates version tag + GitHub Release

**Metrics from last run**:
- 71 unit tests passing
- SonarQube quality gate: PASS
- Trivy: 2–4 CRITICAL CVEs (known Spring Boot 3.2.4 issues, non-blocking)

---

## Slide 7 — Service Mesh: Istio (Phase 3 — Bonus)

**Why Istio?**
- mTLS STRICT enforced across all 3 namespaces — zero plaintext internal traffic
- Circuit breaker (outlierDetection) + retry policy on every service
- Canary traffic splits via VirtualService weights
- Kiali, Jaeger, Prometheus, Grafana from `istio/samples/addons`

**What it gave us for free**:
- Satisfies resilience pattern requirement (circuit breaker + retry)
- Satisfies sidecar pattern requirement (Envoy proxy)
- Satisfies most of the security mTLS requirement
- Live service topology graph in Kiali

*[Screenshot: Kiali graph with mTLS lock icons — docs/diagrams/kiali-graph.png]*

---

## Slide 8 — Design Patterns (Phase 5)

| Pattern | Implementation | File Reference |
|---------|---------------|----------------|
| API Gateway | gateway-service routes all external traffic | `k8s/dev/gateway-service.yaml` |
| Database per Service | Each service has its own Postgres schema | `k8s/infra/dev-infrastructure.yaml` |
| Event-Driven | Kafka topics: survey.submitted, circle.fenced, etc. | `docs/patterns/existing.md` |
| Circuit Breaker | Istio DestinationRule outlierDetection | `k8s/istio/*/destination-rules.yaml` |
| Retry | Istio VirtualService retries on 5xx | `k8s/istio/*/virtual-services.yaml` |
| Sidecar | Istio Envoy proxy — mTLS, metrics, retries | `docs/patterns/sidecar.md` |
| External Configuration | ESO + GCP Secret Manager | `k8s/eso/*/external-secrets.yaml` |
| k-Anonymity | dashboard-service privacy filter | `docs/patterns/existing.md` |

---

## Slide 9 — Testing (Phase 6)

**Coverage across 8 services**:
- 71 unit tests (all passing)
- Integration tests via Testcontainers (PostgreSQL, Kafka, Neo4j, Redis)
- E2E tests for all 5 main services

**Performance (Locust)**:
- 2,558 requests | 21.77 RPS | 230ms median | 0% failure rate

**Security (OWASP ZAP)**:
- 0 HIGH findings
- 2 MEDIUM findings (missing HTTP security headers — remediable at Istio layer)

**JaCoCo coverage threshold**: ≥ 60% line coverage enforced in pipeline

---

## Slide 10 — Observability (Phase 7)

*[Screenshot: Grafana dashboard showing request rate and latency]*

**Four pillars**:

| Tool | Purpose | Access |
|------|---------|--------|
| Prometheus + Grafana | Metrics: RPS, latency, JVM heap, error rate | port-forward svc/grafana 3000 |
| ELK Stack | Centralized logs (Filebeat → Elasticsearch → Kibana) | port-forward svc/kibana 5601 |
| Jaeger | Distributed traces (B3 propagation via Istio) | istioctl dashboard jaeger |
| Kiali | Service mesh topology, traffic flow, mTLS status | istioctl dashboard kiali |

**Alerting**: PrometheusRule CRDs for pod restarts, high latency, error rate, JVM heap → Alertmanager → Slack

---

## Slide 11 — Security (Phase 8)

**Defense in depth**:

1. **Istio mTLS STRICT** — all internal traffic encrypted + mutually authenticated
2. **JWT** — gateway validates every inbound request; no unauthenticated service access
3. **RBAC** — 8 ServiceAccounts × 3 envs, each with least-privilege roles
4. **AuthorizationPolicy** — default-deny + explicit allows per service edge
5. **External Secrets Operator** — all credentials in GCP Secret Manager, never in repo
6. **Trivy** — CVE scan on every build + daily CronJob in production
7. **cert-manager** — Let's Encrypt TLS on Istio Ingress Gateway
8. **OWASP ZAP** — automated security baseline scan in stage pipeline

*[Table: known gaps and planned fixes]*

---

## Slide 12 — Cost Analysis

*[Bar chart: cost per environment per month]*

| Environment | Always On | With Scale-to-0 |
|-------------|-----------|----------------|
| Dev | ~$132–230/month | ~$15–20/month |
| Stage | ~$125–220/month | ~$10–15/month |
| Production | ~$57/month | ~$15–20/month |
| Shared (GCS, AR, SM) | ~$7/month | ~$7/month |
| **Total** | **~$320–515/month** | **~$47–62/month** |

**Key savings**: Scale clusters to 0 when not in use (already automated in `ci/session-stop.sh`).

**Further savings available**: Spot VMs (70% cheaper), consolidate dev+stage into one cluster.

---

## Slide 13 — Lessons Learned

1. **Istio probe rewriting is a hidden trap** — tcpSocket probes become HTTP probes after sidecar injection; services without `/actuator/health` fail readiness. Solution: use tcpSocket from the start.

2. **GCP quotas require sequencing** — 12 vCPU limit means only one cluster can be active at a time. Build automation around this constraint early.

3. **enableServiceLinks: false is mandatory for Confluent Kafka** — K8s service env vars conflict with Kafka's `dub` config framework. Hours of debugging saved for future devs.

4. **Terraform partial apply leaves ghost state** — Always use `terraform import` before re-applying when resources exist in GCP but not in state.

5. **Spring Boot startup takes 75–90s on e2-standard-2** — Set `initialDelaySeconds: 90` on all probes or face restart loops on fresh deployments.

6. **Conventional Commits are worth enforcing from day 1** — Automated versioning and release notes only work if the commit format is consistent. Retrofitting is painful.

7. **Istio CNI intercepts exec probes on infrastructure pods** — Use tcpSocket probes for all infrastructure pods in Istio-enabled namespaces; never exec probes.

8. **Service mesh satisfies multiple requirements simultaneously** — Choosing Istio early unlocked circuit breaker, retry, sidecar pattern, mTLS, canary, and observability with a single installation.

---

## Slide 14 — Demo

*[Live demo or recorded demo video]*

Demo agenda:
- `kubectl get pods -A` — all 3 environments
- Kiali live service graph
- Trigger a Jenkins build (or show last build)
- SonarQube quality gate
- Grafana dashboard — live metrics
- Jaeger — sample trace
- GitHub Releases page — versioned releases

---

## Slide 15 — Q&A

Questions?

Repository: https://github.com/lilmagusa17/circle-guard-public
Operations Manual: `docs/operations/README.md`
Architecture: `docs/diagrams/system-overview.md`
