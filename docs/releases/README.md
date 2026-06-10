# Release Notes Index

CircleGuard uses Semantic Versioning (vMAJOR.MINOR.PATCH). Every production release is tagged and documented automatically by the master CI/CD pipeline.

---

## How Releases Are Generated

1. A merge to `master` triggers the `circleguard-master` Jenkins pipeline.
2. After all tests pass and production deployment is approved, `ci/semver.sh` runs:
   - Reads commits since the last `v*` tag using Conventional Commits format.
   - Calculates the version bump (MAJOR / MINOR / PATCH).
   - Creates a git tag via the GitHub API.
3. `ci/release-notes.sh <VERSION>` generates `RELEASE_NOTES_<VERSION>.md` in the repository root, grouping commits by type (feat / fix / chore / refactor / ci / docs).
4. A GitHub Release is created automatically: `gh release create <tag> --notes-file RELEASE_NOTES_<tag>.md`.

See [`docs/operations/versioning.md`](../operations/versioning.md) for the full versioning convention.

---

## GitHub Releases

All published releases with notes are available at:
https://github.com/lilmagusa17/circle-guard-public/releases

---

## Release Notes Files

Release notes files are generated at the repository root as `RELEASE_NOTES_vX.Y.Z.md`. Below is a reference to all known releases:

| Version | Date | Type | Notes File |
|---------|------|------|-----------|
| (see GitHub Releases page for current list) | | | |

To list all tagged releases locally:
```bash
git tag --list 'v*' --sort=-version:refname
```

To view a specific release's notes:
```bash
# List latest 10 releases via GitHub CLI
gh release list --limit 10

# View a specific release
gh release view v1.0.0
```

---

## Release Artifact Contents

Each GitHub Release includes:
- **Tag**: `vMAJOR.MINOR.PATCH` on the merge commit
- **Release notes body**: Grouped by commit type (Features, Bug Fixes, etc.)
- **Attached file**: `RELEASE_NOTES_vX.Y.Z.md` (same content as the notes body)

Docker images are published to Docker Hub at `magusa17/circleguard-<service>:<version>` for each tagged release.
