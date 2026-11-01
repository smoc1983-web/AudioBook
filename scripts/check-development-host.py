#!/usr/bin/env python3
"""Read the laptop's last uploaded recovery snapshot; never mutate the checkout."""
import argparse
import datetime as dt
import json
from pathlib import Path
import subprocess
import sys

p = argparse.ArgumentParser()
p.add_argument('--repo', default=str(Path(__file__).resolve().parents[1]))
p.add_argument('--heartbeat-file', type=Path)
p.add_argument('--max-age', type=int, default=900)
a = p.parse_args()
try:
    if a.heartbeat_file:
        heartbeat = json.loads(a.heartbeat_file.read_text(encoding='utf-8-sig'))
        snapshot = None
    else:
        def git(*args):
            return subprocess.check_output(['git', '-C', a.repo, *args], stderr=subprocess.PIPE, timeout=45).decode().strip()
        git('fetch', '--quiet', '--no-tags', 'origin', 'refs/heads/backup/audiobook-laptop')
        snapshot = git('rev-parse', 'FETCH_HEAD')
        heartbeat = json.loads(git('show', 'FETCH_HEAD:.remote/heartbeat.json'))
    updated = dt.datetime.fromisoformat(heartbeat['updatedAt'].replace('Z', '+00:00'))
    if updated.tzinfo is None:
        raise ValueError('Heartbeat timestamp has no timezone')
    age = (dt.datetime.now(dt.timezone.utc) - updated).total_seconds()
    if age < -120:
        raise ValueError('Heartbeat timestamp is in the future; check clock synchronization')
    fresh = age <= a.max_age
    print(json.dumps(dict(
        status='recent-heartbeat' if fresh else 'stale-heartbeat',
        route='verify-local-host' if fresh else 'cloud-fallback-candidate',
        ageSeconds=round(max(0, age)), snapshotCommit=snapshot,
        updatedAt=heartbeat['updatedAt'],
        omittedNewFiles=heartbeat.get('untrackedNotBackedUp', []),
        note='A heartbeat is not proof that Codex is reachable. Check the actual host connection before selecting it.'
    ), ensure_ascii=False, indent=2))
    sys.exit(0 if fresh else 10)
except Exception as e:
    print(json.dumps(dict(status='check-failed', route='unknown', error=str(e)), ensure_ascii=False))
    sys.exit(20)
