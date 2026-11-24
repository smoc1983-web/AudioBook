"""Build and verify the isolated Audiobook Test release. No keys or passwords are emitted."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile

CERT = "3a4cecd14bb3e983843871766909c3f07b3dc64379800f3e270e4a27d39affb7"
PACKAGE = "com.audiobookdownloader.test"
APK_NAME = "Audiobook-Test.apk"
NUMBER = int(os.environ["TEST_NUMBER"])
assert 101 <= NUMBER <= 999999, "Test release number out of range"
code = 100000 + NUMBER
name = f"4.2.{NUMBER}"
apk = Path("app/build/outputs/apk/release/app-release.apk")
assert apk.is_file(), "Signed release APK not built"
tools = Path(os.environ["ANDROID_HOME"]) / "build-tools/35.0.0"
badging = subprocess.check_output([str(tools / "aapt"), "dump", "badging", str(apk)], text=True)
assert f"package: name='{PACKAGE}' versionCode='{code}' versionName='{name}'" in badging, "Wrong test package or version"
assert "application-label:'Audiobook Test'" in badging, "Test application is not distinguishable"
assert "application-debuggable" not in badging, "Release must not be debuggable"
manifest = subprocess.check_output([str(tools / "aapt"), "dump", "xmltree", str(apk), "AndroidManifest.xml"], text=True)
assert "com.audiobookdownloader.ReleaseNotesActivity" not in manifest, "Obsolete notes window is still registered"
verified = subprocess.check_output([str(tools / "apksigner"), "verify", "--verbose", "--print-certs", str(apk)], text=True)
assert f"Signer #1 certificate SHA-256 digest: {CERT}" in verified, "Wrong signing key"
assert "Number of signers: 1" in verified
assert "Verified using v2 scheme (APK Signature Scheme v2): true" in verified
with zipfile.ZipFile(apk) as z:
    changes = json.loads(z.read("assets/release-notes.json"))["changes"]
assert isinstance(changes, list) and 0 < len(changes) <= 12
assert all(isinstance(x, str) and 0 < len(x) <= 300 for x in changes)
out = Path("dist-test")
out.mkdir(exist_ok=True)
final_apk = out / APK_NAME
shutil.copyfile(apk, final_apk)
digest = hashlib.sha256(final_apk.read_bytes()).hexdigest()
metadata = {
    "packageName": PACKAGE,
    "versionCode": code,
    "versionName": name,
    "size": final_apk.stat().st_size,
    "sha256": digest,
    "certificateSha256": CERT,
    "apk": APK_NAME,
    "commit": os.environ["GITHUB_SHA"],
    "changes": changes,
}
(out / "test-update.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
(out / "release-notes.md").write_text("\n".join("- " + x for x in changes) + "\n", encoding="utf-8")
print(json.dumps({key: metadata[key] for key in ("packageName", "versionCode", "versionName", "size", "sha256", "certificateSha256")}, indent=2))
