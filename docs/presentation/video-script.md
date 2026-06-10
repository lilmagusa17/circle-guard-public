# Video Demo Script — CircleGuard Proyecto Final SE5

Total duration: 28–30 minutes
Format: Screen recording with narration

---

## 0:00–2:00 — Introduction (2 min)

**Narration:**
"Welcome. This is CircleGuard, a university health-monitoring and contact-tracing platform built as the Proyecto Final for Software Engineering 5. CircleGuard processes health surveys, manages contact graphs using Neo4j, and propagates health status changes (Suspect → Probable → Confirmed) through a campus population in real time while preserving individual privacy.

The platform is deployed across three environments on Google Kubernetes Engine with a full DevOps pipeline including automated testing, security scanning, semantic versioning, and a service mesh with Istio.

We will walk through the architecture, then demonstrate the CI/CD pipeline, the live Kubernetes deployment, observability dashboards, and the security posture."

**Show:** Project repository front page on GitHub.

---

## 2:00–5:00 — Architecture Walkthrough (3 min)

**Show:** `docs/diagrams/system-overview.md` rendered in GitHub.

**Narration:**
"CircleGuard has 8 Spring Boot microservices. External traffic enters through an Istio Ingress Gateway. The gateway-service handles QR code campus entry validation, JWT routing, and Redis session management. Auth-service provides JWT authentication with LDAP integration.

The core health logic lives in promotion-service: it traverses Neo4j contact graphs to identify exposed contacts and cascades status changes through Kafka events. Form-service accepts health surveys and produces Kafka events. Notification-service consumes those events and sends emails and alerts. Dashboard-service provides geospatial hotspot analytics with k-anonymity privacy preservation."

**Show:** `docs/diagrams/data-flow.md` — the survey submission sequence diagram.

"Here you can see the full flow: a user submits a health survey, form-service persists it and produces a Kafka event, notification-service sends confirmation, and if symptoms warrant, promotion-service evaluates the contact graph and triggers fence notifications to all exposed contacts."

---

## 5:00–10:00 — Infrastructure Demo (5 min)

**Show:** Terminal — GCP GKE clusters.

```bash
gcloud container clusters list --project=tallerfinal-496702
```

**Narration:**
"We have three GKE clusters: circleguard-dev, circleguard-stage, and circleguard-prod, all in us-central1. Each is provisioned by Terraform."

**Show:** `terraform/` directory structure, then `terraform/envs/dev/main.tf`.

"The infrastructure is fully codified in Terraform with five reusable modules: vpc, gke, artifact_registry, secrets, and iam. Each environment calls these modules with different sizing parameters. Remote state is stored in a GCS bucket."

```bash
cd terraform/envs/dev && terraform show | head -50
```

**Show:** GKE console or `gcloud container node-pools list`.

"Dev and stage clusters scale to zero between sessions to stay within our CPUS_ALL_REGIONS quota of 12. This brings actual cost down to $50–65/month across all three environments."

---

## 10:00–15:00 — CI/CD Pipeline Demo (5 min)

**Show:** Jenkins UI at http://localhost:8080.

**Narration:**
"Three Jenkins pipelines cover the three environments. Let me walk through the master pipeline stages."

**Show:** `ci/Jenkinsfile.master` in the editor or Jenkins Blue Ocean view.

"The master pipeline: Checkout → Build all 8 services with Gradle → SonarQube quality gate → Unit and integration tests in parallel → Docker Build with Trivy vulnerability scan → Push to Docker Hub → Deploy to stage with smoke test → Stage environment verification → Manual production approval gate (30 minute window) → Canary deployment at 10% traffic for 30 minutes → Second approval to promote to 100% → Semantic version tag via ci/semver.sh → GitHub Release creation."

**Show:** SonarQube dashboard at http://localhost:9000.

"SonarQube shows quality metrics for all 8 services. The quality gate passes with zero new critical issues."

**Show:** Trivy scan output in a recent build log.

"Trivy scans identify known CVEs in Spring Boot 3.2.4's bundled Tomcat and Spring Security. These are reported but non-blocking pending a Spring Boot upgrade."

**Show:** `ci/semver.sh`.

"Semantic versioning is fully automated. The script reads Conventional Commits since the last tag, calculates the appropriate bump, creates a git tag via the GitHub API, and publishes a GitHub Release with generated release notes."

---

## 15:00–20:00 — Kubernetes Demo (5 min)

**Show:** Terminal with kubectl.

```bash
kubectl get pods -n circleguard-production
kubectl get pods -n circleguard-dev
kubectl get peerauthentication -A
```

**Narration:**
"All 8 microservices plus 5 infrastructure pods are running in each namespace. Each application pod has 2 containers — the app and the Envoy sidecar injected by Istio."

```bash
kubectl describe pod auth-service-<hash> -n circleguard-production | grep -A5 "Containers:"
```

"Istio enforces STRICT mTLS in all three namespaces. Every pod-to-pod connection is encrypted and mutually authenticated."

**Show:** `k8s/istio/production/authorization-policies.yaml`.

"AuthorizationPolicy implements default-deny with explicit allow policies for each documented service-to-service edge. gateway-service can reach all backends. Any service can call auth-service for JWT validation. Kafka consumers are allowed internal namespace traffic."

**Show:** Kiali dashboard.

```bash
istioctl dashboard kiali
```

"Kiali shows the live service graph with mTLS lock icons on every edge. We can see request rates, error rates, and the traffic distribution across the mesh."

---

## 20:00–25:00 — Observability Demo (5 min)

**Show:** Grafana at http://localhost:3000 (port-forwarded).

**Narration:**
"Grafana shows per-service metrics: request rate, error rate, p50/p95/p99 latency, and JVM heap usage. The Istio mesh dashboard shows the overall traffic flow and response time distribution across the service mesh."

**Show:** Jaeger traces.

```bash
istioctl dashboard jaeger
```

"Jaeger shows distributed traces. We can follow a single health survey submission from gateway-service through form-service to Kafka to notification-service, all in a single trace view."

**Show:** Kibana at http://localhost:5601.

"Kibana aggregates logs from all pods via Filebeat. The `circleguard-*` index shows structured logs from every service. A saved search for ERROR level in the last hour gives an instant health check."

**Show:** `docs/operations/alerts.md`.

"PrometheusRule alerting fires on: pod restart loop, p95 latency over 1 second, error rate over 5%, JVM heap over 90%. All alerts route to Slack via Alertmanager."

---

## 25:00–28:00 — Security Demo (3 min)

**Show:** `k8s/rbac/production/rbac.yaml`.

**Narration:**
"Each of the 8 microservices has its own ServiceAccount with a Role granting only get and list on configmaps and secrets within its own namespace. No service can read another service's secrets."

**Show:** `k8s/eso/production/external-secrets.yaml`.

"All Kubernetes Secrets are sourced from GCP Secret Manager via the External Secrets Operator. No plaintext credentials exist in the repository or in any Kubernetes manifest."

```bash
grep -rE "password:|secret:" k8s/dev/ | head -5
# Should show only ExternalSecret references, no values
```

**Show:** `k8s/security/trivy-scan-cronjob.yaml`.

"A daily CronJob at 06:00 UTC scans all 8 production images for HIGH and CRITICAL CVEs and posts a Slack report."

**Show:** `k8s/security/gateway-tls.yaml`.

"cert-manager manages Let's Encrypt TLS certificates for the Istio Ingress Gateway. The ClusterIssuer uses HTTP-01 challenge via the Istio ingress class."

---

## 28:00–30:00 — Lessons Learned and Close (2 min)

**Show:** `docs/lessons-learned.md`.

**Narration:**
"Key lessons from this project:

Istio adds significant complexity — sidecar probe rewriting caused restart loops on first deployment that took time to diagnose. The fix was using tcpSocket probes for infrastructure and `holdApplicationUntilProxyStarts` for application pods.

GCP quota management was critical. With only 12 vCPUs across all regions, we had to serialize cluster operations and build session start/stop scripts.

Conventional Commits saved the versioning work almost entirely — once the format was established, semantic versioning and release notes were fully automated.

The service mesh gave us mTLS, circuit breaking, retries, and canary deployments essentially for free once Istio was installed, which satisfied multiple project requirements with a single technology choice.

Thank you."

---

## Demo Checklist (Before Recording)

- [ ] All production pods are Running: `kubectl get pods -n circleguard-production`
- [ ] Jenkins is started: `docker start circleguard-jenkins`
- [ ] SonarQube is accessible: http://localhost:9000
- [ ] Kiali port-forward is active: `istioctl dashboard kiali`
- [ ] Grafana port-forward is active: `kubectl port-forward -n monitoring svc/grafana 3000:3000`
- [ ] Kibana port-forward is active: `kubectl port-forward -n logging svc/kibana-kibana 5601:5601`
- [ ] Terminal windows are prepared with kubeconfig set to production cluster
- [ ] Screen resolution set to 1920×1080 for recording clarity
