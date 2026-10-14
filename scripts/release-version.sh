#!/usr/bin/env bash
# Derive release version info from a release tag.
# Usage: scripts/release-version.sh v1.2.3[-rc1]
# Prints KEY=VALUE lines (append to $GITHUB_OUTPUT in CI):
#   version_name=1.2.3[-rc1]  version_code=10203  prerelease=true|false
set -euo pipefail

tag="${1:-}"
re='^v(([0-9]+)\.([0-9]+)\.([0-9]+)(-[0-9A-Za-z.-]+)?)$'
if [[ ! $tag =~ $re ]]; then
  echo "error: tag '$tag' is not of the form vMAJOR.MINOR.PATCH[-suffix]" >&2
  exit 1
fi

name="${BASH_REMATCH[1]}"
major=$((10#${BASH_REMATCH[2]}))
minor=$((10#${BASH_REMATCH[3]}))
patch=$((10#${BASH_REMATCH[4]}))
if (( minor > 99 || patch > 99 )); then
  echo "error: minor and patch must be <= 99 for versionCode" >&2
  exit 1
fi

echo "version_name=$name"
echo "version_code=$((major * 10000 + minor * 100 + patch))"
if [[ -n ${BASH_REMATCH[5]} ]]; then echo "prerelease=true"; else echo "prerelease=false"; fi
