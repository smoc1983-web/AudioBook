#!/usr/bin/env bash
set -euo pipefail
# 69 and 89 were distributed outside the normal workflow. Reserve both numbers:
# the next raw run88 publishes90 rather than reusing the laptop's89.
release_run=${GITHUB_RUN_NUMBER:?GitHub run number is required}
[[ "$release_run" =~ ^[0-9]+$ ]] || { echo 'Invalid GitHub run number' >&2; exit 1; }
export GITHUB_RUN_NUMBER=$((10#$release_run + 2))
exec "$@"
