#!/usr/bin/env bash
# Reads conventional commits since last tag, computes next semver, creates tag + release notes.
# Usage: bash ci/semver.sh [--dry-run]
set -euo pipefail

DRY_RUN=false
[[ "${1:-}" == "--dry-run" ]] && DRY_RUN=true

LAST_TAG=$(git describe --tags --abbrev=0 2>/dev/null || echo "v0.0.0")
echo "Last tag: $LAST_TAG" >&2

# Parse current version components
VERSION_CORE="${LAST_TAG#v}"
MAJOR=$(echo "$VERSION_CORE" | cut -d. -f1)
MINOR=$(echo "$VERSION_CORE" | cut -d. -f2)
PATCH=$(echo "$VERSION_CORE" | cut -d. -f3)

# Get commits since last tag (or all commits if no prior tag)
if [[ "$LAST_TAG" == "v0.0.0" ]]; then
    COMMITS=$(git log --pretty=format:"%s" 2>/dev/null || echo "")
else
    COMMITS=$(git log "${LAST_TAG}..HEAD" --pretty=format:"%s" 2>/dev/null || echo "")
fi

if [[ -z "$COMMITS" ]]; then
    echo "No commits since $LAST_TAG. Nothing to release." >&2
    echo "$LAST_TAG"
    exit 0
fi

BUMP="patch"
while IFS= read -r msg; do
    [[ -z "$msg" ]] && continue
    if echo "$msg" | grep -qE "^(feat|fix|refactor|perf)(\(.+\))?!:" || echo "$msg" | grep -q "BREAKING CHANGE"; then
        BUMP="major"
    elif echo "$msg" | grep -qE "^feat(\(.+\))?:" && [[ "$BUMP" != "major" ]]; then
        BUMP="minor"
    fi
done <<< "$COMMITS"

case "$BUMP" in
    major) MAJOR=$((MAJOR + 1)); MINOR=0; PATCH=0 ;;
    minor) MINOR=$((MINOR + 1)); PATCH=0 ;;
    patch) PATCH=$((PATCH + 1)) ;;
esac

NEW_TAG="v${MAJOR}.${MINOR}.${PATCH}"
echo "New version: $NEW_TAG (bump: $BUMP)" >&2

if [[ "$DRY_RUN" == "true" ]]; then
    echo "$NEW_TAG"
    exit 0
fi

# Generate release notes grouped by Conventional Commit type
NOTES_FILE="RELEASE_NOTES_${NEW_TAG}.md"
{
    echo "# Release ${NEW_TAG}"
    echo ""
    echo "**Released:** $(date -u '+%Y-%m-%d %H:%M UTC')"
    echo ""

    FEAT_LINES=$(echo "$COMMITS" | grep -E "^feat(\(.+\))?:" || true)
    FIX_LINES=$(echo "$COMMITS" | grep -E "^fix(\(.+\))?:" || true)
    CHORE_LINES=$(echo "$COMMITS" | grep -E "^(chore|docs|refactor|ci|test|build)(\(.+\))?:" || true)
    BREAKING_LINES=$(echo "$COMMITS" | grep -E "^.+!:|BREAKING CHANGE" || true)

    if [[ -n "$BREAKING_LINES" ]]; then
        echo "## ⚠️ Breaking Changes"
        while IFS= read -r line; do
            [[ -z "$line" ]] && continue
            echo "- $line"
        done <<< "$BREAKING_LINES"
        echo ""
    fi

    if [[ -n "$FEAT_LINES" ]]; then
        echo "## ✨ Features"
        while IFS= read -r line; do
            [[ -z "$line" ]] && continue
            echo "- $line"
        done <<< "$FEAT_LINES"
        echo ""
    fi

    if [[ -n "$FIX_LINES" ]]; then
        echo "## 🐛 Bug Fixes"
        while IFS= read -r line; do
            [[ -z "$line" ]] && continue
            echo "- $line"
        done <<< "$FIX_LINES"
        echo ""
    fi

    if [[ -n "$CHORE_LINES" ]]; then
        echo "## 🔧 Other Changes"
        while IFS= read -r line; do
            [[ -z "$line" ]] && continue
            echo "- $line"
        done <<< "$CHORE_LINES"
        echo ""
    fi
} > "$NOTES_FILE"

echo "Generated: $NOTES_FILE" >&2
cat "$NOTES_FILE"

# Create git tag
git config user.email "ci@circleguard.local" 2>/dev/null || true
git config user.name "CircleGuard CI" 2>/dev/null || true
git tag -a "$NEW_TAG" -m "Release $NEW_TAG"
echo "Tagged: $NEW_TAG" >&2

# Publish tag and release via GitHub API (requires GH_TOKEN env var)
if [[ -n "${GH_TOKEN:-}" ]]; then
    REPO="lilmagusa17/circle-guard-public"
    COMMIT_SHA=$(git rev-parse HEAD)

    gh api repos/${REPO}/git/refs \
        -X POST \
        -f ref="refs/tags/${NEW_TAG}" \
        -f sha="${COMMIT_SHA}" \
    && echo "Tag published via GitHub API" >&2

    gh release create "$NEW_TAG" \
        --repo "$REPO" \
        --notes-file "$NOTES_FILE" \
        --title "CircleGuard $NEW_TAG" \
    && echo "GitHub Release created: $NEW_TAG" >&2
fi

# Save version for pipeline steps
echo "$NEW_TAG" > /tmp/new-version
echo "$NEW_TAG"
