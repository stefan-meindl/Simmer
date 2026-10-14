#!/usr/bin/env bash
# Behavior test for scripts/release-version.sh (its CLI is the seam).
# Usage: scripts/test-release-version.sh
set -uo pipefail

script="$(dirname "$0")/release-version.sh"
fails=0

expect_ok() { # tag expected-output (newline separated)
  local tag="$1" want="$2" got
  if got=$("$script" "$tag" 2>/dev/null) && [[ "$got" == "$want" ]]; then
    echo "ok   $tag"
  else
    echo "FAIL $tag: wanted [$want], got [${got:-}]"; fails=$((fails + 1))
  fi
}

expect_reject() {
  if "$script" "$1" >/dev/null 2>&1; then
    echo "FAIL $1: should be rejected"; fails=$((fails + 1))
  else
    echo "ok   $1 rejected"
  fi
}

expect_ok v1.2.3 $'version_name=1.2.3\nversion_code=10203\nprerelease=false'
expect_ok v0.1.0-rc1 $'version_name=0.1.0-rc1\nversion_code=100\nprerelease=true'
expect_ok v10.20.30 $'version_name=10.20.30\nversion_code=102030\nprerelease=false'
expect_reject v1.2
expect_reject foo
expect_reject v1.100.0
expect_reject v1.0.100
expect_reject ""

if (( fails )); then echo "$fails failure(s)"; exit 1; fi
echo "all passed"
