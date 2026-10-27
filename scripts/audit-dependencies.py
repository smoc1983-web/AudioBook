"""Check a resolved Maven runtime inventory against OSV; no credentials or publication."""
import argparse
import json
from pathlib import Path
import urllib.request

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    modules = json.loads(args.input.read_text())
    queries = [{"package": {"ecosystem": "Maven", "name": row["name"]},
                "version": row["version"]} for row in modules]
    report = {"source": "https://api.osv.dev/v1/querybatch", "complete": False,
              "modules": modules, "findings": []}
    try:
        for start in range(0, len(queries), 100):
            batch = queries[start:start + 100]
            request = urllib.request.Request(report["source"],
                data=json.dumps({"queries": batch}).encode(),
                headers={"Content-Type": "application/json"})
            with urllib.request.urlopen(request, timeout=30) as response:
                result = json.load(response)["results"]
            if len(result) != len(batch):
                raise ValueError("Incomplete vulnerability response")
            for offset, row in enumerate(result):
                if row.get("vulns"):
                    report["findings"].append({**modules[start + offset],
                                               "advisories": row["vulns"]})
        report["complete"] = True
    except Exception as error:
        report["error"] = type(error).__name__ + ": " + str(error)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps({"complete": report["complete"], "modules": len(modules),
                      "findings": report["findings"]}))
    return 0 if report["complete"] and not report["findings"] else 1

if __name__ == "__main__":
    raise SystemExit(main())
