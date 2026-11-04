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
