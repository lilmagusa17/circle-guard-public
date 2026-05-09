#!/usr/bin/env bash
# generate-release-notes.sh
# Genera CHANGELOG.md entre el último tag y HEAD siguiendo Keep a Changelog.

set -euo pipefail

VERSION="${BUILD_NUMBER:-$(date +%Y%m%d%H%M)}"
DATE=$(date +%Y-%m-%d)
OUTPUT="CHANGELOG.md"

# Obtener el tag anterior; si no existe, usar el primer commit
PREV_TAG=$(git describe --tags --abbrev=0 HEAD^ 2>/dev/null || git rev-list --max-parents=0 HEAD)
CURRENT_TAG="v1.0.${VERSION}"

echo "# Changelog" > "$OUTPUT"
echo "" >> "$OUTPUT"
echo "## [${CURRENT_TAG}] - ${DATE}" >> "$OUTPUT"
echo "" >> "$OUTPUT"

# Clasificar commits por prefijo convencional
for type in feat fix perf refactor test chore; do
  case $type in
    feat)     label="### Added" ;;
    fix)      label="### Fixed" ;;
    perf)     label="### Performance" ;;
    refactor) label="### Changed" ;;
    test)     label="### Tests" ;;
    chore)    label="### Chore" ;;
  esac

  commits=$(git log "${PREV_TAG}..HEAD" --pretty=format:"- %s (%h)" --grep="^${type}" 2>/dev/null || true)
  if [ -n "$commits" ]; then
    echo "$label" >> "$OUTPUT"
    echo "$commits" >> "$OUTPUT"
    echo "" >> "$OUTPUT"
  fi
done

# Commits sin prefijo convencional
echo "### Other" >> "$OUTPUT"
git log "${PREV_TAG}..HEAD" --pretty=format:"- %s (%h)" \
  | grep -vE "^- (feat|fix|perf|refactor|test|chore)" >> "$OUTPUT" || true

echo ""
echo "Release Notes generado: $OUTPUT"
cat "$OUTPUT"
