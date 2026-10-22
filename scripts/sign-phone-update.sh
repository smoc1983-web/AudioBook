#!/usr/bin/env bash
# Run in Code on the Go's terminal. The original private key stays on the phone.
set -euo pipefail
if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo 'Usage: bash sign-phone-update.sh Audiobook-stable-UNSIGNED.apk [old-stable.apk]' >&2
  exit 2
fi
candidate=$1
[[ -f "$candidate" ]] || { echo 'Candidate APK missing' >&2; exit 1; }
signing_key=${AUDIOBOOK_SIGNING_KEYSTORE:-"$HOME/.android/debug.keystore"}
[[ -f "$signing_key" ]] || {
  echo 'Original signing key not found. Select the key used by the old project via AUDIOBOOK_SIGNING_KEYSTORE. Do not generate a replacement key or uninstall Audiobook.' >&2
  exit 1
}
# The second argument is only needed if Android does not allow reading its installed APK.
old_apk=${2:-}
if [[ -z "$old_apk" ]]; then
  old_apk=$(/system/bin/pm path com.audiobookdownloader 2>/dev/null | sed -n 's/^package://p' | head -n 1) || true
fi
[[ -r "$old_apk" ]] || { echo 'Cannot read installed stable APK. Export its APK and pass its path as argument 2. Do not uninstall the app.' >&2; exit 1; }
sdk_root=${ANDROID_HOME:-"$HOME/android-sdk"}
build_tools=$(find "$sdk_root/build-tools" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V | tail -n 1) || true
[[ -x "$build_tools/apksigner" && -x "$build_tools/zipalign" && -x "$build_tools/aapt" ]] || {
  echo 'Android SDK build-tools (apksigner, zipalign, aapt) are required in Code on the Go.' >&2; exit 1
}
package_name() { "$build_tools/aapt" dump badging "$1" | sed -n "s/^package: name='\([^']*\)'.*/\1/p"; }
version_code() { "$build_tools/aapt" dump badging "$1" | sed -n "s/^package:.* versionCode='\([0-9]*\)'.*/\1/p"; }
fingerprint() { "$build_tools/apksigner" verify --print-certs "$1" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | tr 'A-F' 'a-f'; }
[[ $(package_name "$candidate") == com.audiobookdownloader && $(package_name "$old_apk") == com.audiobookdownloader ]] || {
  echo 'Wrong application package. Only stable Audiobook can be updated.' >&2; exit 1
}
new_version=$(version_code "$candidate");old_version=$(version_code "$old_apk")
[[ "$new_version" =~ ^[0-9]+$ && "$old_version" =~ ^[0-9]+$ && "$new_version" -gt "$old_version" ]] || {
  echo 'Candidate version must be newer than the installed app.' >&2; exit 1
}
old_fingerprint=$(fingerprint "$old_apk")
[[ "$old_fingerprint" =~ ^[0-9a-f]{64}$ ]] || { echo 'Cannot verify original APK certificate.' >&2; exit 1; }
output_dir=$(cd "$(dirname "$candidate")" && pwd)
output="$output_dir/Audiobook-update.apk"
[[ ! -e "$output" ]] || { echo 'Audiobook-update.apk already exists. Keep or rename it before continuing.' >&2; exit 1; }
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
"$build_tools/zipalign" -p -f 4 "$candidate" "$work/aligned.apk"
echo 'Enter the ORIGINAL signing key password locally (default Gradle debug key: android).'
"$build_tools/apksigner" sign --ks "$signing_key" --ks-key-alias "${AUDIOBOOK_SIGNING_ALIAS:-androiddebugkey}" --out "$work/signed.apk" "$work/aligned.apk"
new_fingerprint=$(fingerprint "$work/signed.apk")
[[ "$new_fingerprint" == "$old_fingerprint" ]] || {
  echo 'SIGNATURE MISMATCH. No update was exported or installed. Use the original key; do not uninstall Audiobook.' >&2; exit 1
}
"$build_tools/apksigner" verify --verbose "$work/signed.apk"
cp -n "$work/signed.apk" "$output"
echo "Verified update ready: $output"
echo 'Open this APK in the Android file manager and select Update. Keep the installed app and its library.'
