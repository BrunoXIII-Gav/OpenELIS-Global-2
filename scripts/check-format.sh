#!/bin/bash
# Use the same whole-repository format checks locally and in CI.
set -euo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

if ! mvn -B -ntp spotless:check; then
    echo "Backend formatting failed. Run: mvn spotless:apply" >&2
    exit 1
fi

if ! npm --prefix frontend run check-format; then
    echo "Frontend formatting failed. Run: npm --prefix frontend run format" >&2
    exit 1
fi
