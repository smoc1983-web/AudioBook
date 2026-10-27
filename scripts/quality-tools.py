"""Install pinned ub-mode tools or invoke one without changing the global Python."""

import hashlib
import io
import json
from pathlib import Path
import subprocess
import sys
import tarfile
import urllib.request
import venv

ROOT = Path(__file__).resolve().parents[1]
TOOL_HOME = ROOT / ".quality-tools"
LOCK = ROOT / "docs/quality/tools.lock.json"
TOOLS = ("crapper", "dryer", "mutator")


def install():
    if sys.version_info < (3, 12):
        raise SystemExit("Use Python 3.12 or newer for installation.")
    lock = json.loads(LOCK.read_text())
    TOOL_HOME.mkdir(exist_ok=True)
    (TOOL_HOME / "installed-lock.sha256").unlink(missing_ok=True)
    venv.create(TOOL_HOME / "venv", with_pip=True)
    python = str(TOOL_HOME / "venv/bin/python")
    packages = [f"{name}=={version}" for name, version in lock["packages"].items()]
    subprocess.run([python, "-m", "pip", "install", *packages], check=True)
    sources = []
    for name, pin in lock["tools"].items():
        url = f'https://codeload.github.com/{pin["repository"]}/tar.gz/{pin["commit"]}'
        with urllib.request.urlopen(url, timeout=60) as response:
            data = response.read(8 * 1024 * 1024 + 1)
        if len(data) > 8 * 1024 * 1024:
            raise SystemExit(f"Archive too large: {name}")
        if hashlib.sha256(data).hexdigest() != pin["archive_sha256"]:
            raise SystemExit(f"Archive hash mismatch: {name}")
        with tarfile.open(fileobj=io.BytesIO(data)) as archive:
            archive.extractall(TOOL_HOME / "sources", filter="data")
        sources.append(str(TOOL_HOME / "sources" / f'{name}-{pin["commit"]}'))
    subprocess.run([python, "-m", "pip", "install", "--no-deps",
                    "--no-build-isolation", *sources], check=True)
    subprocess.run([python, "-m", "pip", "check"], check=True)
    (TOOL_HOME / "installed-lock.sha256").write_text(hashlib.sha256(LOCK.read_bytes()).hexdigest())
    print("Installed pinned Crapper, Dryer and Mutator.")


def main():
    if len(sys.argv) < 2 or sys.argv[1] not in (*TOOLS, "install"):
        raise SystemExit("Usage: python3 scripts/quality-tools.py install|crapper|dryer|mutator [options]")
    tool = sys.argv[1]
    if tool == "install":
        install()
        return
    marker = TOOL_HOME / "installed-lock.sha256"
    if not marker.exists() or marker.read_text() != hashlib.sha256(LOCK.read_bytes()).hexdigest():
        raise SystemExit("Run: python3 scripts/quality-tools.py install")
    raise SystemExit(subprocess.call([str(TOOL_HOME / "venv/bin" / tool), *sys.argv[2:]], cwd=ROOT))


if __name__ == "__main__":
    main()
