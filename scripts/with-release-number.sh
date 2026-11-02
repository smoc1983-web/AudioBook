#!/usr/bin/env bash
set -euo pipefail
# Historical audit workflows keep the original run+1 mapping. The main build freezes
# a remote-tag-aware number once per Actions attempt, including manual public releases.
release_run=${GITHUB_RUN_NUMBER:?GitHub run number is required}
[[ "$release_run" =~ ^[0-9]+$ ]] || { echo 'Invalid GitHub run number' >&2; exit 1; }
if [ -n "${AUDIOBOOK_RELEASE_RUN:-}" ]; then
  [[ "$AUDIOBOOK_RELEASE_RUN" =~ ^[1-9][0-9]*$ ]] || { echo 'Invalid reserved release number' >&2; exit 1; }
  release_run="$AUDIOBOOK_RELEASE_RUN"
elif [ "${GITHUB_ACTIONS:-}" = true ] && [ "${GITHUB_WORKFLOW:-}" = 'Build Audiobook APK' ]; then
  run_id=${GITHUB_RUN_ID:?}; attempt=${GITHUB_RUN_ATTEMPT:?}
  [[ "$run_id" =~ ^[0-9]+$ && "$attempt" =~ ^[0-9]+$ ]] || { echo 'Invalid Actions attempt' >&2; exit 1; }
  number_file="${RUNNER_TEMP:?}/audiobook-release-$run_id-$attempt.txt"
  if [ ! -f "$number_file" ]; then
    refs_file=$(mktemp "$RUNNER_TEMP/preview-tags.XXXXXX")
    trap 'rm -f "$refs_file"' EXIT
    git ls-remote --tags origin > "$refs_file"
    python scripts/reserve-preview-version.py "$GITHUB_RUN_NUMBER" < "$refs_file" > "$number_file.tmp"
    mv "$number_file.tmp" "$number_file"
  fi
  release_run=$(cat "$number_file")
  [[ "$release_run" =~ ^[1-9][0-9]*$ ]] || { echo 'Invalid frozen release number' >&2; exit 1; }
else
  release_run=$((10#$release_run + 1))
fi
export GITHUB_RUN_NUMBER="$release_run"
exec "$@"
