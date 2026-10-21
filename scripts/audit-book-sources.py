"""Record external availability separately from deterministic adapter/runtime checks.
An unavailable site must not prevent shipping a UI or startup repair.
"""
import concurrent.futures
import datetime
import json
import re
import urllib.error
import urllib.request
from pathlib import Path

SOURCES = {
    "Азбука веры": "https://azbyka.ru/audio/",
    "Книгоблуд": "https://mob.knigoblud.club/",
    "Bookinfo": "https://bookinfo.net/",
    "Audiokniga-online.ru": "https://audiokniga-online.ru/",
}

def inspect(item):
    name, address = item
    result = {"source": name, "url": address}
    try:
        request = urllib.request.Request(address, headers={
            "User-Agent": "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36",
            "Accept-Language": "ru-RU,ru;q=0.9",
        })
        with urllib.request.urlopen(request, timeout=20) as response:
            text = response.read(3 * 1024 * 1024).decode("utf-8", errors="replace")
            result.update(status=response.status, final_url=response.geturl())
            title = re.search(r"<title[^>]*>(.*?)</title>", text, re.I | re.S)
            result["title"] = re.sub(r"\s+", " ", title[1]).strip() if title else ""
            result["access_challenge"] = bool(re.search(r"just a moment|access denied|checking your browser|captcha", result["title"], re.I))
            result["has_html"] = bool(re.search(r"<(?:html|body|main)\b", text, re.I))
    except (urllib.error.URLError, TimeoutError, OSError) as error:
        result["error"] = str(error)[:350]
    return result

if __name__ == "__main__":
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        reports = list(pool.map(inspect, SOURCES.items()))
    output = {"checked_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
              "scope": "Home-page availability only; not proof of search, download or playback", "sources": reports}
    Path("source-evidence").mkdir(exist_ok=True)
    Path("source-evidence/availability.json").write_text(json.dumps(output, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(output, ensure_ascii=False, indent=2))
