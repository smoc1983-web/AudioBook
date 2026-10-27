"""Fail on actual lint Error/Fatal findings, independently of CI step conclusion."""
import collections
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

def main():
    path=Path(sys.argv[1])
    issues=ET.parse(path).getroot().findall("issue")
    counts=collections.Counter(row.get("severity") for row in issues)
    errors=[row for row in issues if row.get("severity") in ("Error","Fatal")]
    print("Android lint findings:",dict(counts))
    for row in errors:
        print(row.get("id"),row.get("message"))
    return 1 if errors else 0

if __name__=="__main__":
    raise SystemExit(main())
