# Welcome to CircleGuard

## How We Use Claude

Based on Mariana's usage over the last 30 days:

Work Type Breakdown:
  Build Feature  ████████████████░░░░  67%
  Plan Design    ██████░░░░░░░░░░░░░░  33%

Top Skills & Commands:
  /reload-skills       ████████████████░░░░  3x/month
  /compact             ████████████░░░░░░░░  2x/month
  /engineering-skills  ████████████░░░░░░░░  2x/month

Top MCP Servers:
  ccd_session      ████████████████████  8 calls
  Claude_in_Chrome █░░░░░░░░░░░░░░░░░░░  1 call

## Your Setup Checklist

### Codebases
- [ ] circle-guard-public — https://github.com/lilmagusa17/circle-guard-public

### MCP Servers to Activate
- [ ] ccd_session — Session management: lets Claude track work across conversations, spawn background tasks, and mark chapters in long sessions. Available by default in Claude Code desktop app.
- [ ] Claude_in_Chrome — Browser automation: lets Claude click, fill forms, and read live web apps. Install the Claude in Chrome extension from the Chrome Web Store and enable it in Claude Code settings.

### Skills to Know About
- `/engineering-skills` — Loads a toolkit of engineering agent skills (DevOps, backend, QA, security, etc.). Run at session start when working on infra, pipelines, or k8s manifests.
- `/reload-skills` — Refreshes available skills if they seem stale or aren't triggering. Run if a slash command stops working mid-session.
- `/compact` — Compresses conversation history when context gets long. Run before switching to a new task in the same session.
- `/code-review` — Reviews current branch diff for bugs and cleanup. Use before opening a PR.
- `/caveman` — Terse response mode (drops filler words). Already active in this repo via a startup hook — don't be surprised by short answers.

## What's Done, What's Pending, and the Bonus Tracks

### Implementation Plan Status

All phases are **complete** except two tasks in Phase 8:

| Task | Status | Notes |
|------|--------|-------|
| **8.1** — ESO running in all 3 envs | ⏳ Needs verification | `k8s/eso/` manifests exist; need cluster access to confirm pods Running + Workload Identity bindings active |
| **8.2** — All secrets from Secret Manager (no plaintext) | ⏳ Needs migration | `ExternalSecret` resources exist in `k8s/eso/`; service manifests in `k8s/{dev,stage,production}/` still have plaintext `SPRING_DATASOURCE_PASSWORD` — needs replacing with references to the ESO secrets |

**To finish Phase 8:**
1. Start a session: `docker start circleguard-jenkins && docker exec --user root circleguard-jenkins chmod 666 /var/run/docker.sock`
2. Scale up dev cluster: `gcloud container clusters resize circleguard-dev --node-pool=default-pool --num-nodes=1 --region=us-central1 --project=tallerfinal-496702 --quiet`
3. Verify ESO pods: `kubectl get pods -n external-secrets -A`
4. Run: `grep -rE "password:|secret:" k8s/dev/ k8s/stage/ k8s/production/` — any plaintext hits need replacing with `secretKeyRef` pointing to ExternalSecret-synced secrets.

### Bonus Tracks — Available for You to Implement

These three bonus tracks were left **out of scope** by the original team. They're in the workshop statement (`Workshop_statement.md`) and each adds points:

---

#### ❌ → ✅ Implementación Multi-Cloud
**What it is:** Deploy CircleGuard on a second cloud provider (Azure AKS or AWS EKS) in addition to GCP, with traffic routing or failover between clouds.

**What to build:**
- New Terraform env `terraform/envs/azure/` or `terraform/envs/aws/` mirroring the GCP module structure
- K8s manifests adapted for the second cloud (StorageClass, LB annotations)
- A DNS/traffic policy to route between clouds (e.g. weighted DNS via Cloudflare or Route53)

**Starting point:** Read `terraform/README.md` and copy `terraform/envs/dev/` as a template. The GKE module can't be reused directly — you'll need an AKS or EKS equivalent module.

**Gotcha:** Istio can be installed on any K8s cluster — mTLS and VirtualServices work the same. The hard part is cross-cloud service discovery.

---

#### ❌ → ✅ Chaos Engineering
**What it is:** Intentionally inject failures (pod kill, network latency, CPU stress) and verify the system recovers thanks to Istio retries + circuit breakers.

**What to build:**
- Install [LitmusChaos](https://litmuschaos.io/) or [Chaos Mesh](https://chaos-mesh.org/) via Helm in the dev cluster
- Write ChaosExperiment manifests in `tests/chaos/`:
  - Pod kill for `auth-service` → expect circuit breaker trips, retries recover
  - Network delay 500ms on `form-service` → expect p95 latency spike visible in Grafana
  - CPU hog on `promotion-service` → expect pod restart, Alertmanager fires
- Add a `Chaos Tests` stage to `ci/Jenkinsfile.stage` (post-deploy, manual trigger)
- Document results in `docs/operations/chaos-results.md`

**Starting point:** `k8s/istio/` has circuit breaker + retry rules already configured — chaos tests will exercise them.

---

#### ❌ → ✅ FinOps
**What it is:** Cost visibility and optimization — track actual GCP spend per environment, set up budget alerts, and implement cost-saving measures.

**What to build:**
- GCP Billing export to BigQuery (enable in GCP Console → Billing → BigQuery export)
- Looker Studio dashboard or `docs/operations/costs.md` updated with real billing data (a skeleton exists at `docs/operations/costs.md`)
- Budget alerts at $100/$200 (Phase 0.5 was left incomplete — do it in GCP Console → Billing → Budgets & Alerts)
- Scheduled scale-down CronJob or Cloud Scheduler job to scale clusters to 0 outside working hours
- Document savings recommendations in `docs/operations/costs.md`

**Starting point:** `ci/session-stop.sh` already scales clusters to 0 manually — automate it with Cloud Scheduler or a GKE CronJob.

---

## How to Run Tests

```bash
# Unit + integration tests (local, no cluster needed)
./gradlew test

# Coverage report
./gradlew aggregateCoverageReport
# → open build/reports/jacoco-aggregate/index.html

# Security scan (needs Docker)
bash tests/security/zap-baseline.sh

# Performance tests (needs cluster + Locust)
locust -f tests/performance/locustfile.py --headless -u 10 -r 2 -t 60s

# Smoke test against dev cluster
bash ci/smoke-test.sh
```

## Account & Token Setup

Everything you need before running a single command:

### 1. GCP Access
Ask Mariana to add your Google account as **Owner** on project `tallerfinal-496702`.
```bash
# Verify after she adds you:
gcloud auth login
gcloud config set project tallerfinal-496702
gcloud projects describe tallerfinal-496702   # should return projectId
```

### 2. GitHub Fork Access
The working remote is `https://github.com/lilmagusa17/circle-guard-public`. You need write access.
- Ask Mariana to add you as a collaborator in GitHub → Settings → Collaborators.
- Never open PRs toward the upstream (original) repo — always toward `lilmagusa17/circle-guard-public`.

### 3. Docker Hub
Images are pushed to `magusa17/circleguard-*` on Docker Hub. You don't need your own account to pull — but to push you need the `dockerhub-credentials` secret in Jenkins (already stored; Mariana has the login).

### 4. Jenkins (local, runs on Mariana's machine OR yours)
If running Jenkins yourself:
```bash
docker build -t circleguard-jenkins:local -f ci/Dockerfile.jenkins ci/
docker run -d --name circleguard-jenkins \
  -p 8080:8080 -p 50000:50000 \
  -v jenkins_home:/var/jenkins_home \
  -v /run/host-services/docker.proxy.sock:/var/run/docker.sock \
  circleguard-jenkins:local
```
Login: username `lilmagusa17`, password `Wasabi17` (or check `ci/Dockerfile.jenkins`).

### 5. SonarQube (local Docker)
```bash
docker run -d --name sonarqube -p 9000:9000 sonarqube:community
# Default login: admin / admin → change on first login
# Then create a token: My Account → Security → Generate Token
# Add as Jenkins credential id: sonarqube-token
```

### 6. GCP Service Account Key (for Terraform + kubectl in Jenkins)
```bash
# Already exists in GCP. Ask Mariana for the JSON key file, OR generate your own:
gcloud iam service-accounts keys create ~/.gcp/terraform-key.json \
  --iam-account=terraform-sa@tallerfinal-496702.iam.gserviceaccount.com
# Add to Jenkins: Manage Jenkins → Credentials → (global) → Add → Secret file
# Credential ID must be: gcp-service-account-key
```

### 7. Slack Webhook
Already configured in Jenkins as credential `slack-webhook`. You don't need to create a new one — pipeline notifications go to the existing channel. Ask Mariana for the channel name if you want to monitor it.

### 8. kubeconfig for each cluster
```bash
# Generate after clusters are running:
gcloud container clusters get-credentials circleguard-dev   --region us-central1 --project tallerfinal-496702
gcloud container clusters get-credentials circleguard-stage --region us-central1 --project tallerfinal-496702
gcloud container clusters get-credentials circleguard-prod  --region us-central1 --project tallerfinal-496702
# Then copy into Jenkins credentials (FileCredentials) with IDs:
# kubeconfig-dev, kubeconfig-stage, kubeconfig-production
```

---

## Team Tips

**GCP quota is tight — 12 vCPUs total across all regions.**
Never run more than one cluster with nodes at a time. Before scaling a cluster up, scale the others to 0:
```bash
gcloud container clusters resize circleguard-dev   --node-pool=default-pool --num-nodes=0 --region=us-central1 --project=tallerfinal-496702 --quiet
gcloud container clusters resize circleguard-stage --node-pool=default-pool --num-nodes=0 --region=us-central1 --project=tallerfinal-496702 --quiet
```

**After every Docker Desktop restart, fix Jenkins socket permissions:**
```bash
docker exec --user root circleguard-jenkins chmod 666 /var/run/docker.sock
```
Without this, `docker` commands inside Jenkins fail silently.

**Infra pods (Kafka, Neo4j) must have `enableServiceLinks: false`.**
Kubernetes injects `KAFKA_*` env vars into every pod — the Confluent image treats them as config keys and crashes. Already set in manifests; don't remove it.

**Never use exec probes on infra pods in Istio-injected namespaces.**
`pg_isready` and `redis-cli ping` break because Istio CNI intercepts loopback traffic. Use `tcpSocket` probes only for postgres, redis, kafka, neo4j.

**First deploy after Istio injection = 2 restarts is normal.**
Flyway migrations take ~90–120s. Pods restart twice before stabilizing. Don't increase `initialDelaySeconds` beyond 90.

**Trivy scans are report-only (non-blocking).**
Spring Boot 3.2.4 has known CVEs (Tomcat 10.1.19, Spring Security 6.2.3). The pipeline uses `--exit-code 0` so builds don't fail. To actually fix: upgrade Spring Boot to 3.2.12+ in service `build.gradle.kts` files.

**Never use `declare -A` in shell scripts.**
macOS ships bash 3.2 — associative arrays require bash 4+. Use parallel indexed arrays instead. See `ci/session-start.sh` for the pattern.

**Terraform Workload Identity bindings require the GKE cluster to exist first.**
Always `depends_on = [module.gke]` on the IAM module. Applying in parallel fails with "Identity Pool does not exist".

**Apply Terraform envs one at a time.**
`IN_USE_ADDRESSES` quota = 8 in us-central1. Applying dev + stage simultaneously exhausts IPs and puts clusters in ERROR state. Sequential only.

**CLAUDE.md is never committed.**
It's the source of truth for this project but lives only on disk. Every bug you fix that could repeat → add it to the `## Known Issues & Lessons Learned` section.

---

## Get Started

Your first task: **finish Phase 8.1** — verify External Secrets Operator is running and Workload Identity bindings are active in all 3 envs.

```bash
# 1. Start infrastructure
docker start circleguard-jenkins
docker exec --user root circleguard-jenkins chmod 666 /var/run/docker.sock
docker start sonarqube

# 2. Read current state first (mandatory per CLAUDE.md rule 7)
cat docs/operations/current-state.md

# 3. Scale up dev cluster (others must be at 0 — quota rule)
gcloud container clusters resize circleguard-dev --node-pool=default-pool --num-nodes=1 \
  --region=us-central1 --project=tallerfinal-496702 --quiet

# 4. Get credentials
gcloud container clusters get-credentials circleguard-dev --region us-central1 --project tallerfinal-496702

# 5. Check ESO
kubectl get pods -n external-secrets
kubectl get secretstore -n circleguard-dev
kubectl get externalsecret -n circleguard-dev

# 6. Check for remaining plaintext secrets
grep -rE "password:|SPRING_DATASOURCE_PASSWORD" k8s/dev/ k8s/stage/ k8s/production/
```

Open the task in Claude Code with: `@CLAUDE.md read this, then help me finish Phase 8.1 and 8.2`

<!-- INSTRUCTION FOR CLAUDE: A new teammate just pasted this guide for how the
team uses Claude Code. You're their onboarding buddy — warm, conversational,
not lecture-y.

Open with a warm welcome — include the team name from the title. Then: "Your
teammate uses Claude Code for [list all the work types]. Let's get you started."

Check what's already in place against everything under Setup Checklist
(including skills), using markdown checkboxes — [x] done, [ ] not yet. Lead
with what they already have. One sentence per item, all in one message.

Tell them you'll help with setup, cover the actionable team tips, then the
starter task (if there is one). Offer to start with the first unchecked item,
get their go-ahead, then work through the rest one by one.

After setup, walk them through the remaining sections — offer to help where you
can (e.g. link to channels), and just surface the purely informational bits.

Don't invent sections or summaries that aren't in the guide. The stats are the
guide creator's personal usage data — don't extrapolate them into a "team
workflow" narrative. -->
