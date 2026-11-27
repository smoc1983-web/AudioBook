import json
import re
import sys
from collections import Counter
from pathlib import Path
from urllib.parse import urlsplit


def url_key(url):
    parts = urlsplit(url)
    host = (parts.hostname or "").removeprefix("www.")
    if host == "mob.knigoblud.club":
        host = "knigoblud.club"
    return host, parts.path.rstrip("/")


root = Path(sys.argv[1])
browser_file = root / "browser-series/browser-series.jsonl"
rows = [json.loads(line) for line in browser_file.read_text().splitlines() if line.strip()]
for slug, title in (("fayroll", "Файролл"), ("arhil", "Архил"), ("builder", "Путь строителя")):
    reports = [row for row in rows if row["query"] == title]
    print(f"\n{title}: browser search outcomes {dict(Counter(row['status'] for row in reports))}")
    browser = [edition for row in reports for edition in row["editions"]]
    browser_started = {url_key(edition["url"]) for edition in browser if edition["secondChapter"] == "started"}
    found_urls = {url_key(edition["url"]) for edition in browser}
    print(f"Browser confirmed links: {len(found_urls)}; chapter 2 started: {len(browser_started)}")
    print(f"Browser player outcomes: {dict(Counter(edition['secondChapter'] for edition in browser))}")
    path = root / f"app-{slug}/matrix-all.txt"
    if not path.is_file():
        print("App report unavailable")
        continue
    log = path.read_text()
    runs = list(re.finditer(r"SEARCH all requested=\d+ completed=true elapsedMs=\d+", log))
    if not runs:
        print("App did not complete search")
        continue
    log = log[runs[-1].end():]
    app = {}
    url = None
    for line in log.splitlines():
        edition = re.search(r"EDITION all ([\d.]+|null) (https?://\S+)", line)
        if edition:
            url = url_key(edition[2])
        play = re.search(r"PLAY all ([\d.]+|null) .*?reason=(\w+) started=(true|false)", line)
        if play and url:
            app[url] = (play[1], play[2], play[3] == "true")
            url = None
    print(f"App audio links: {len(app)}; chapter 2 started: {sum(value[2] for value in app.values())}")
    print(f"App playable cycle positions: {sorted({value[0] for value in app.values() if value[2]})}")
    # Cases that played in the independent browser in run 45933581235.
    # Report even when today's browser controls cannot be verified again.
    reference_cases = {
        "arhil": {("audiopolka.club", "/3079826"), ("knigoblud.club", "/14142663-90fc-4b62-828f-8543ee03b7ce")},
        "builder": {("audiopolka.club", "/1459212")},
    }
    for known in sorted(reference_cases.get(slug, ())):
        print("Previously playable link:", *known, "app =", app.get(known, "link absent"))
    for missing in sorted(browser_started):
        observed = app.get(missing)
        if observed is None or not observed[2]:
            print("Browser playback discrepancy:", *missing, "app =", observed or "link absent")
