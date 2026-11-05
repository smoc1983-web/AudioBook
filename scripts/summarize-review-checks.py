"""Publish bounded test diagnostics as workflow annotations; never read credentials."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

def emit(kind, title, text):
    text = re.sub(r"https?://[^\s<>]+", "[URL]", text)[:1200]
    escaped = text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
    print(f"::{kind} title={title}::{escaped}")

for directory in ("app/build/test-results", "app/build/outputs/androidTest-results"):
    files = list(Path(directory).rglob("TEST-*.xml"))
    totals = [0, 0, 0, 0]
    for file in files:
        root = ET.parse(file).getroot()
        for i, key in enumerate(("tests", "failures", "errors", "skipped")):
            totals[i] += int(root.get(key, "0"))
        for case in root.iter("testcase"):
            for failure in list(case.findall("failure")) + list(case.findall("error")):
                emit("error", "Test failure", case.get("classname", "") + "." + case.get("name", "") + ": " + failure.get("message", ""))
    if files:
        emit("notice", "Test totals", f"{directory}: tests={totals[0]}, failures={totals[1]}, errors={totals[2]}, skipped={totals[3]}")

for file in Path("app/build/reports").glob("lint-results-*.xml"):
    issues = ET.parse(file).getroot().findall("issue")
    counts = {severity: sum(i.get("severity") == severity for i in issues) for severity in ("Fatal", "Error", "Warning", "Information")}
    emit("notice", "Lint totals", f"{file.name}: {counts}")
    for issue in issues:
        if issue.get("severity") in ("Fatal", "Error"):
            location = issue.find("location")
            where = "" if location is None else f"{location.get('file', '')}:{location.get('line', '')} "
            emit("error", "Lint failure", where + issue.get("id", "") + ": " + issue.get("message", ""))

for log in (Path("review-build.log"), Path("review-android-build.log")):
    if not log.is_file():
        continue
    for line in log.read_text(errors="replace").splitlines():
        if re.match(r"(?:e: |.*\.(?:kt|java):[0-9]+:.*error:|> Task .* FAILED)", line):
            emit("error", "Build diagnostic", line)
