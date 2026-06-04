# CircleGuard – Branching Strategy

## Model: GitHub Flow

CircleGuard follows **GitHub Flow** — a lightweight, single-trunk workflow optimised for continuous delivery.

### Core rules

| Rule | Detail |
|------|--------|
| **One long-lived branch** | `master` is the only permanent branch. It is always deployable. |
| **Feature work** | Branch off `master` with prefix `feat/<short-description>` (e.g. `feat/istio-mtls`). |
| **Bug fixes** | Branch off `master` with prefix `fix/<short-description>` (e.g. `fix/kafka-reconnect`). |
| **Documentation / chores** | `docs/<topic>` or `chore/<topic>`. |
| **No long-lived `develop`** | There is no permanent `develop` branch. Stage deployments are triggered by direct pushes to `master` or manually through the Jenkins UI. |
| **Merge via Pull Request** | Every branch must be merged via a PR with at least one reviewer approval before landing on `master`. |
| **Delete after merge** | Feature and fix branches are deleted immediately after merge. |

### Branch naming convention

```
feat/<kebab-case-description>
fix/<kebab-case-description>
docs/<kebab-case-description>
chore/<kebab-case-description>
```

Examples:
```
feat/terraform-gke-modules
feat/istio-mtls-strict
fix/prometheus-scrape-config
docs/observability-runbook
chore/update-gradle-wrapper
```

### Lifecycle

```
master ──────────────────────────────────────────────► (always deployable)
          │                       ▲
          └── feat/my-feature ────┘  (PR + merge → delete branch)
```

### Mapping to Jenkins pipelines

| Branch pattern | Jenkins pipeline | Trigger |
|----------------|-----------------|---------|
| `feat/*`, `fix/*`, `docs/*`, `chore/*` (any non-master) | `circleguard-dev` | Automatic on push |
| `master` | `circleguard-stage` | Automatic on push to `master` |
| `master` (tagged `v*.*.*`) | `circleguard-master` | Manual or on semver tag creation |

### Protected branches

`master` must be configured with the following branch protection rules in GitHub:

- Require pull request reviews before merging (min 1 approval).
- Require status checks to pass (circleguard-dev pipeline).
- No direct pushes (except from CI service account).
- No force-push.

### Hotfix process

A hotfix is a `fix/*` branch created from `master`, pushed, PR-reviewed, and merged. There is no separate hotfix branch type. If the fix must reach production immediately, the master pipeline is triggered manually after merge.

### Conventional Commits standard

All commit messages follow the [Conventional Commits](https://www.conventionalcommits.org/) spec to enable automatic semantic versioning:

| Prefix | Version bump | Example |
|--------|-------------|---------|
| `feat:` | MINOR | `feat(auth): add JWT refresh token support` |
| `fix:` | PATCH | `fix(kafka): retry on connection timeout` |
| `chore:`, `docs:`, `ci:`, `refactor:` | PATCH | `docs: update observability runbook` |
| `feat!:` or `BREAKING CHANGE:` | MAJOR | `feat!: replace REST with gRPC between services` |
