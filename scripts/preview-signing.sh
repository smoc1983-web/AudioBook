#!/usr/bin/env bash
set -euo pipefail
signing_dir="$(dirname "$PREVIEW_KEYSTORE")"
mkdir -p "$signing_dir"
if [ -n "${KEYSTORE_BASE64:-}" ]; then
  printf '%s' "$KEYSTORE_BASE64" | base64 --decode > "$PREVIEW_KEYSTORE"
fi
if [ ! -s "$PREVIEW_KEYSTORE" ]; then
  # Recover from a private Actions artifact if GitHub evicted the cache.
  for run in $(gh run list --repo "$GITHUB_REPOSITORY" --workflow build-apk.yml --limit 20 --json databaseId --jq '.[].databaseId'); do
    if gh run download "$run" --repo "$GITHUB_REPOSITORY" --name preview-signing-recovery --dir "$signing_dir" 2>/dev/null; then break; fi
  done
fi
if [ ! -s "$PREVIEW_KEYSTORE" ]; then
  # Once a channel exists, never silently rotate its key: installed apps must remain updatable.
  count=$(gh api "repos/$GITHUB_REPOSITORY/releases?per_page=100" --jq '[.[] | select(.tag_name | startswith("preview-v"))] | length')
  if [ "$count" != "0" ]; then
    echo 'Preview signing key unavailable. Restore AUDIOBOOK_PREVIEW_KEYSTORE_BASE64 from the private recovery artifact; refusing to break installed updates.' >&2
    exit 1
  fi
  keytool -genkeypair -keystore "$PREVIEW_KEYSTORE" -storepass android -keypass android -alias audiobook-preview -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Audiobook Preview,OU=Preview,O=Audiobook'
fi
keytool -list -keystore "$PREVIEW_KEYSTORE" -storepass android -alias audiobook-preview
