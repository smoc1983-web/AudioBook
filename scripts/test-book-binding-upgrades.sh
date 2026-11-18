#!/usr/bin/env bash
set -euo pipefail
export GITHUB_RUN_NUMBER="${PREVIEW_CANDIDATE_NUMBER:?A candidate version is required}"
for previous in 95 96; do
    baseline=upgrade-baseline
    if [[ "$previous" == 96 ]]; then
        baseline=upgrade-baseline-96
    fi
    PREVIEW_UPGRADE_BASELINE_DIR="$baseline" \
        PREVIEW_UPGRADE_EVIDENCE_DIR="startup-evidence/upgrade-$previous" \
        python3 scripts/test-preview-upgrade.py
done
