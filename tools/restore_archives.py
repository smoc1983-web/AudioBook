#!/usr/bin/env python3
"""Restore three historical ZIPs from tracked parts; verify SHA-256 throughout."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import tempfile


def digest(path):
    h = hashlib.sha256()
    with path.open('rb') as source:
        for block in iter(lambda: source.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


def restore(root, output):
    manifest = json.loads((root / 'releases/ARCHIVE-PARTS.json').read_text())
    for archive in manifest['archives']:
        destination = output / archive['path']
        if destination.exists():
            if destination.stat().st_size != archive['size'] or digest(destination) != archive['sha256']:
                raise ValueError(f'Existing file differs; kept unchanged: {destination}')
            print(f'OK: {archive["path"]}')
            continue
        destination.parent.mkdir(parents=True, exist_ok=True)
        fd, temporary = tempfile.mkstemp(prefix=destination.name + '.', suffix='.tmp', dir=destination.parent)
        try:
            h = hashlib.sha256()
            size = 0
            with os.fdopen(fd, 'wb') as target:
                for part in archive['parts']:
                    source = root / part['path']
                    ph = hashlib.sha256()
                    part_size = 0
                    with source.open('rb') as stream:
                        for block in iter(lambda: stream.read(1024 * 1024), b''):
                            target.write(block)
                            ph.update(block)
                            h.update(block)
                            size += len(block)
                            part_size += len(block)
                    if part_size != part['size'] or ph.hexdigest() != part['sha256']:
                        raise ValueError(f'Part checksum mismatch: {source}')
            if size != archive['size'] or h.hexdigest() != archive['sha256']:
                raise ValueError(f'Archive checksum mismatch: {archive["path"]}')
            # A hard link publishes the verified file without replacing an existing one.
            os.link(temporary, destination)
            print(f'Restored: {archive["path"]}')
        finally:
            Path(temporary).unlink(missing_ok=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, help='Restore to another folder, useful for verification.')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    restore(root, args.output.resolve() if args.output else root)
