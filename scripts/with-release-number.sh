#!/usr/bin/env bash
set -euo pipefail
# 69 was distributed manually. Normal workflow run69 must therefore publish70;
# the offset remains stable for subsequent runs and every child build/test/tool.
release_run=${GITHUB_RUN_NUMBER:?GitHub run number is required}
[[ "$release_run" =~ ^[0-9]+$ ]] || { echo 'Invalid GitHub run number' >&2; exit 1; }
export GITHUB_RUN_NUMBER=$((10#$release_run + 1))
exec "$@"
