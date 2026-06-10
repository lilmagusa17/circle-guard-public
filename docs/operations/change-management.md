# Change Management Process — CircleGuard

---

## Overview

CircleGuard follows **GitHub Flow**: all changes flow through feature/fix branches that are merged to `master` via pull request. The CI/CD pipeline enforces quality gates automatically; production deployments require a manual approval step.

---

## Who Can Request a Change

Any developer with write access to the fork repository may open a pull request. Changes are categorized by their scope:

| Change Type | Branch Naming | Example |
|-------------|--------------|---------|
| New feature | `feat/<short-description>` | `feat/jwt-refresh-tokens` |
| Bug fix | `fix/<short-description>` | `fix/kafka-consumer-timeout` |
| Hotfix (urgent prod fix) | `fix/hotfix-<description>` | `fix/hotfix-auth-null-pointer` |
| Infrastructure change | `feat/infra-<description>` | `feat/infra-add-node-pool` |
| CI/CD update | `feat/ci-<description>` | `feat/ci-add-trivy-stage` |

---

## Who Approves

- **Pull Request Approval**: Required from at least one repository maintainer (team lead) before merge.
- **Production Deployment Approval**: A designated approver must click "Approve" in the Jenkins `Prod Approval` input gate within 30 minutes.
- **Emergency Hotfixes**: Can bypass normal PR review with team lead verbal approval, but must have at least one approver on the PR before merge.

---

## Quality Gates

All gates must pass before a change reaches production. Gates are enforced in order:

### Gate 1 — Code Quality (SonarQube)

- Runs in DEV and STAGE pipelines on every branch push.
- Quality gate criteria: no new critical/blocker issues, code coverage not decreased by more than 5%.
- Failure: pipeline stops, Slack notification sent to `#circleguard-ci` channel.
- Bypass: Not possible. Fix the issues before merging.

### Gate 2 — Vulnerability Scanning (Trivy)

- Runs after Docker build on every pipeline.
- Scans for HIGH and CRITICAL CVEs in all 8 service images.
- Currently non-blocking (`--exit-code 0`): findings are reported but pipeline continues. This is due to known CVEs in Spring Boot 3.2.4 that have no available fix at the current version pin.
- Graduation criteria: once Spring Boot is upgraded to 3.2.12+, set `--exit-code 1` to block on HIGH/CRITICAL.

### Gate 3 — Automated Tests

- **Unit tests**: All must pass. Failure blocks the pipeline immediately.
- **Integration tests**: Run in STAGE pipeline using Testcontainers. Must pass.
- **E2E tests**: Run in MASTER pipeline. Must pass.
- **Coverage threshold**: Line coverage must be >= 60% (enforced via JaCoCo + Jenkins JaCoCo plugin).

### Gate 4 — Stage Deployment Verification

- After deploying to `circleguard-stage`, the pipeline runs `ci/smoke-test.sh` against the stage namespace.
- All 8 services must respond on their TCP ports within 5 attempts.
- Failure triggers rollback of the stage deployment and Slack notification.

### Gate 5 — Manual Production Approval

- The master pipeline pauses at the `Prod Approval` stage for up to 30 minutes.
- An approver reviews: SonarQube dashboard, Trivy scan output, and stage deployment health.
- If not approved within 30 minutes, the pipeline times out and the deployment is cancelled (no production change).

### Gate 6 — Canary Gate

- On approval, the new version is deployed as a canary at 10% of production traffic (via Istio VirtualService weight split).
- The canary runs for 30 minutes.
- A second manual approval is required to promote from 10% to 100%.
- If the second approval is denied or times out, the canary is rolled back automatically.

---

## Change Flow

```
Developer creates feat/fix branch
        ↓
Pushes to branch → DEV pipeline triggers automatically
        ↓
DEV pipeline: Build → SonarQube → Tests → Trivy → Docker Push → Deploy to circleguard-dev
        ↓
Developer opens Pull Request to master
        ↓
Team lead reviews code, approves PR
        ↓
Merge to master → MASTER pipeline triggers
        ↓
MASTER pipeline: Build → SonarQube → Tests → Trivy → Docker Push → Deploy to circleguard-stage
        ↓
Smoke test passes on stage
        ↓
[GATE 5] Manual Prod Approval (30 min window)
        ↓
Canary deploy to production (10% traffic, 30 min)
        ↓
[GATE 6] Canary Promotion Approval
        ↓
Full production deployment (100% traffic)
        ↓
ci/semver.sh generates tag + RELEASE_NOTES_vX.Y.Z.md
        ↓
GitHub Release published automatically
```

---

## Hotfix Process

For urgent production fixes that cannot wait for the full flow:

1. Create branch `fix/hotfix-<description>` from `master`.
2. Make the minimal necessary fix.
3. Push — DEV pipeline runs automatically.
4. Open PR with `[HOTFIX]` prefix in title.
5. Team lead reviews and approves (verbal fast-track acceptable for P0 incidents).
6. Merge to master — MASTER pipeline runs.
7. Monitor canary at 10% for 5 minutes (shortened window for hotfix).
8. Approve promotion to 100%.

---

## How Rollback Is Triggered

See [rollback.md](rollback.md) for exact commands.

Rollback scenarios:
- **Canary failure**: Deny the canary promotion gate in Jenkins. Pipeline rolls back VirtualService weights automatically.
- **Post-promotion failure**: Use `kubectl rollout undo` and patch the VirtualService manually.
- **Database migration failure**: Flyway migrations are versioned; run `flyway repair` and `flyway undo` (if reversible migration exists).

---

## Communication

All pipeline events are posted to Slack via the `slack-webhook` Jenkins credential:
- Build start: not notified (too noisy)
- Build failure: immediate notification with stage name and build URL
- Prod Approval gate open: notification with approval link
- Production deploy success: notification with version tag and release notes link
- Rollback triggered: immediate notification

Slack channel: configured in Jenkins `slack-webhook` credential (see `docs/operations/notifications.md`).
