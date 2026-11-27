import re
import sys
from pathlib import Path


def playback(root: Path, mode: str) -> set[int]:
    log = (root / f"fayroll-{mode}" / f"matrix-{mode}.txt").read_text(encoding="utf-8")
    searches = list(re.finditer(rf"SEARCH {mode} requested=\d+ completed=true elapsedMs=(\d+)", log))
    if not searches:
        raise ValueError(f"{mode}: missing completed search")
    current = log[searches[-1].end():]
    volumes = {
        int(position)
        for position in re.findall(rf"PLAY {mode} (\d+)\.0 [^\n]*\bchapter=2\b[^\n]*\bstarted=true\b", current)
    }
    if not volumes:
        raise ValueError(f"{mode}: no streaming playback confirmed")
    print(f"{mode}: {int(searches[-1][1]) / 1000:.1f}s, playable positions {sorted(volumes)}")
    return volumes


def main(root: Path) -> int:
    all_positions = playback(root, "all")
    expected = set().union(*(playback(root, mode) for mode in ("baseline", "pair", "knigoblud", "bookinfo")))
    missing = expected - all_positions
    if missing:
        print(f"Positions missing from the all-source playback check: {sorted(missing)}", file=sys.stderr)
    return int(bool(missing))


if __name__ == "__main__":
    sys.exit(main(Path(sys.argv[1])))
