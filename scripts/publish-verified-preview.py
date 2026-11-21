"""Publish the verified Preview APK without replacing another release or its assets."""
import hashlib
import json
import os
import re
import subprocess
import zipfile
from pathlib import Path

REPOSITORY = "smoc1983-web/AudioBook"
PACKAGE = "com.audiobookdownloader.preview"
CERTIFICATE = "3a4cecd14bb3e983843871766909c3f07b3dc64379800f3e270e4a27d39affb7"


def gh(*args):
    return subprocess.check_output(["gh", *args], text=True).strip()


def api(path):
    return json.loads(gh("api", f"repos/{REPOSITORY}/{path}"))


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def validate_candidate(root, version, source, run_id, artifact_id):
    run = api(f"actions/runs/{run_id}")
    assert run["conclusion"] == "success" and run["head_sha"] == source
    artifact = api(f"actions/artifacts/{artifact_id}")
    assert not artifact["expired"] and artifact["name"] == f"Audiobook-Preview-{version}-candidate"
    assert artifact["workflow_run"]["head_sha"] == source and artifact["workflow_run"]["id"] == run_id

    apk = root / "Audiobook-Preview.apk"
    metadata = root / "preview-update.json"
    manifest = json.loads(metadata.read_text())
    expected = {
        "packageName": PACKAGE, "versionCode": code(version), "versionName": version,
        "apk": apk.name, "commit": source, "size": apk.stat().st_size,
        "sha256": digest(apk), "certificateSha256": CERTIFICATE,
    }
    assert all(manifest.get(key) == value for key, value in expected.items())
    assert manifest["sha256"] == os.environ["APPROVED_APK_SHA"] and "parts" not in manifest
    with zipfile.ZipFile(apk) as archive:
        changes = json.loads(archive.read("assets/release-notes.json"))["changes"]
    assert changes == manifest["changes"] and 0 < len(changes) <= 12
    assert all(isinstance(line, str) and 0 < len(line) <= 300 for line in changes)

    evidence = json.loads((root.parent / "startup-evidence" / "installed-upgrade.json").read_text())
    assert evidence["baselineCode"] == code(os.environ["APPROVED_BASELINE"])
    assert evidence["newCode"] == code(version)
    for key in ("oldUpdaterOpenedSystemConfirmation", "androidPackageReplacementSucceeded",
                "certificateUnchanged", "privateAudioFileChecksumPreserved"):
        assert evidence[key] is True

    tools = Path(os.environ["ANDROID_HOME"]) / "build-tools/35.0.0"
    badging = subprocess.check_output([tools / "aapt", "dump", "badging", apk], text=True)
    assert f"package: name='{PACKAGE}' versionCode='{code(version)}' versionName='{version}'" in badging
    assert "application-debuggable" not in badging
    verified = subprocess.check_output([tools / "apksigner", "verify", "--verbose", "--print-certs", apk], text=True)
    assert f"Signer #1 certificate SHA-256 digest: {CERTIFICATE}" in verified
    assert "Number of signers: 1" in verified
    assert "Verified using v2 scheme (APK Signature Scheme v2): true" in verified
    return manifest, {apk.name: apk, metadata.name: metadata}


def code(version):
    assert re.fullmatch(r"4\.2\.[1-9][0-9]*", version)
    return 100000 + int(version.rsplit(".", 1)[1])


def validate_assets(release, files, complete=False):
    names = set()
    for asset in release["assets"]:
        path = files[asset["name"]]
        assert asset["state"] == "uploaded" and asset["size"] == path.stat().st_size
        assert asset["digest"] == f"sha256:{digest(path)}"
        names.add(asset["name"])
    if complete:
        assert names == files.keys()
    return names


def publish(root, version, source, manifest, files):
    tag = f"preview-v{code(version)}"
    refs = api("git/matching-refs/tags/preview-v")
    for ref in refs:
        name = ref["ref"].removeprefix("refs/tags/")
        if name == tag:
            assert ref["object"]["sha"] == source
        elif re.fullmatch(r"preview-v[1-9][0-9]*", name):
            assert int(name.removeprefix("preview-v")) < code(version)
    releases = api("releases?per_page=100")
    exact = [release for release in releases if release["tag_name"] == tag]
    if not exact:
        assert not any(ref["ref"] == f"refs/tags/{tag}" for ref in refs)
        notes = root / "release-notes.md"
        notes.write_text("\n".join(f"- {line}" for line in manifest["changes"]) + "\n", encoding="utf-8")
        gh("release", "create", tag, "--repo", REPOSITORY, "--target", source, "--draft", "--prerelease",
           "--title", f"Audiobook {version}", "--notes-file", str(notes))
        exact = [release for release in api("releases?per_page=100") if release["tag_name"] == tag]
    release, = exact
    assert release["target_commitish"] == source and release["prerelease"]
    if release["draft"]:
        uploaded = validate_assets(release, files)
        for name in files.keys() - uploaded:
            gh("release", "upload", tag, str(files[name]), "--repo", REPOSITORY)
        release = api(f"releases/{release['id']}")
        assert release["draft"]
        validate_assets(release, files, complete=True)
        gh("release", "edit", tag, "--repo", REPOSITORY, "--draft=false", "--prerelease", "--latest=false")

    release = api(f"releases/tags/{tag}")
    assert not release["draft"] and release["prerelease"]
    assert api(f"git/ref/tags/{tag}")["object"]["sha"] == source
    validate_assets(release, files, complete=True)
    return release


def main():
    assert os.environ["GITHUB_REPOSITORY"] == REPOSITORY
    version = os.environ["APPROVED_VERSION"]
    source = os.environ["APPROVED_SOURCE"]
    assert re.fullmatch(r"[0-9a-f]{40}", source)
    run_id = int(os.environ["APPROVED_RUN"])
    artifact_id = int(os.environ["APPROVED_ARTIFACT"])
    root = Path("candidate/dist")
    manifest, files = validate_candidate(root, version, source, run_id, artifact_id)
    release = publish(root, version, source, manifest, files)
    proof = {**manifest, "url": release["html_url"], "tag": release["tag_name"],
             "releaseId": release["id"], "apkBuildRun": run_id, "apkArtifactId": artifact_id}
    (root / "publication.json").write_text(json.dumps(proof, ensure_ascii=False, indent=2) + "\n")
    print(release["html_url"])


if __name__ == "__main__":
    main()
