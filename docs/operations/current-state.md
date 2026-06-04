# CircleGuard — Current Deployed State

Live record of what is actually deployed. Update this file every time infrastructure changes.

**Last updated:** 2026-06-03

---

## GCP Project

- **Project ID:** `circleguard-final`
- **Region:** `us-central1`
- **Billing account:** `019044-EE5C1C-F61E8F`

## Terraform State Bucket

- **Bucket:** `gs://circle-guard-tfstate-final`
- **Status:** Must be created manually before first `terraform init` (see `terraform/README.md`)

## GKE Clusters

| Cluster | Status | Nodes | Notes |
|---------|--------|-------|-------|
| `circleguard-dev` | Not yet applied | 0 | Modules created, apply pending |
| `circleguard-stage` | Not yet applied | 0 | Modules created, apply pending |
| `circleguard-prod` | Not yet applied | 0 | Modules created, apply pending |

## Phase Completion

| Phase | Status | Notes |
|-------|--------|-------|
| Phase 0 — Foundation | 🔴 Not started | Prereqs needed |
| Phase 1 — Terraform | 🟡 In progress | Modules written; apply pending |
| Phase 2 — K8s Migration | 🔴 Not started | Needs Phase 1 |
| Phase 3 — Istio | 🔴 Not started | Needs Phase 2 |
| Phase 4 — CI/CD | 🔴 Not started | Needs Phase 2+3 |
| Phase 5 — Patterns | 🔴 Not started | Needs Phase 3 |
| Phase 6 — Testing | 🔴 Not started | Needs Phase 4 |
| Phase 7 — Observability | 🔴 Not started | Needs Phase 2+3 |
| Phase 8 — Security | 🔴 Not started | Needs Phase 3+4 |
| Phase 9 — Change Mgmt | 🔴 Not started | Needs Phase 4 |
| Phase 10 — Docs | 🔴 Not started | Needs all phases |

## Namespaces

Not yet deployed.

## Active Services

None — clusters not yet running.

## Jenkins

- Container: `circleguard-jenkins` (local Docker)
- Status: Stopped between sessions
- Start: `docker start circleguard-jenkins && docker exec --user root circleguard-jenkins chmod 666 /var/run/docker.sock`

## SonarQube

- Container: `sonarqube` (local Docker)
- Status: Stopped between sessions
- Start: `docker start sonarqube`

## Next Action

1. Ensure GCP project `circleguard-final` exists and APIs are enabled (Phase 0.1–0.2)
2. Create state bucket: `gsutil mb -l us-central1 gs://circle-guard-tfstate-final && gsutil versioning set on gs://circle-guard-tfstate-final`
3. `cd terraform/envs/dev && terraform init && terraform apply`
4. Update this file after apply succeeds
