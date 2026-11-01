#!/usr/bin/env python3
"""Verify the original Preview identity, then publish to the laptop's public directory."""
import argparse
import hashlib
import html
import json
import os
from pathlib import Path
import re
import runpy
import shutil
import subprocess
import zipfile

CERTIFICATE = '3a4cecd14bb3e983843871766909c3f07b3dc64379800f3e270e4a27d39affb7'
ORIGIN = 'https://audiobook-laptop.tail1dbbf9.ts.net'

def atomic_text(path, text):
    temporary = path.with_suffix(path.suffix + '.tmp')
    temporary.write_text(text, encoding='utf-8')
    os.replace(temporary, path)

def atomic_copy(source, target):
    temporary = target.with_suffix(target.suffix + '.tmp')
    shutil.copyfile(source, temporary)
    os.replace(temporary, target)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--directory', type=Path, required=True)
    parser.add_argument('--build-tools', type=Path, required=True)
    parser.add_argument('--release-number', type=int, required=True)
    args = parser.parse_args()
    audit = runpy.run_path(str(Path(__file__).with_name('audit-installable-apk.py')))
    report = audit['audit'](args.apk, args.build_tools)
    identity = subprocess.check_output([str(args.build_tools/'aapt'), 'dump', 'badging', str(args.apk)], text=True)
    match = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", identity)
    if not match or int(match[2]) != 100000 + args.release_number or match[3] != f'4.2.{args.release_number}':
        raise ValueError('APK version differs from requested release')
    if "application-label:'Audiobook'" not in identity:
        raise ValueError('Unexpected application label')
    signer = args.build_tools/('apksigner.bat' if os.name == 'nt' else 'apksigner')
    signature = subprocess.check_output([str(signer), 'verify', '--verbose', '--print-certs', str(args.apk)], text=True)
    if f'Signer #1 certificate SHA-256 digest: {CERTIFICATE}' not in signature:
        raise ValueError('Original Preview signing certificate does not match')
    with zipfile.ZipFile(args.apk) as archive:
        if not any(ORIGIN.encode() in archive.read(name) for name in archive.namelist() if re.fullmatch(r'classes\d*\.dex', name)):
            raise ValueError('Laptop update origin missing from compiled APK')
    sha = hashlib.file_digest(args.apk.open('rb'), 'sha256').hexdigest()
    metadata = {'packageName': match[1], 'versionCode': int(match[2]), 'versionName': match[3],
                'size': args.apk.stat().st_size, 'sha256': sha, 'certificateSha256': CERTIFICATE,
                'apk': 'Audiobook-Preview.apk',
                'parts': [{'url': f'{match[3]}/Audiobook-Preview.apk', 'size': args.apk.stat().st_size, 'sha256': sha}],
                'notes': 'Новый значок «Книга в наушниках». Обновления загружаются с ноутбука; Tailscale на телефоне не требуется.'}
    updates = args.directory/'updates'
    updates.mkdir(parents=True, exist_ok=True)
    manifest = updates/'preview-update.json'
    if manifest.exists():
        previous = json.loads(manifest.read_text(encoding='utf-8'))
        if previous['versionCode'] > metadata['versionCode'] or (previous['versionCode'] == metadata['versionCode'] and previous['sha256'] != sha):
            raise ValueError('Refusing to regress or reuse an already published version')
    version_dir = updates/match[3]
    version_dir.mkdir(exist_ok=True)
    atomic_copy(args.apk, version_dir/'Audiobook-Preview.apk')
    atomic_copy(args.apk, updates/'Audiobook-Preview.apk')
    atomic_text(manifest, json.dumps(metadata, ensure_ascii=False, indent=2)+'\n')
    page = f'''<!doctype html><html lang="ru"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Audiobook {match[3]} — обновление с ноутбука</title><style>body{{font:18px/1.6 system-ui;background:#141c24;color:#eaf5f2;max-width:580px;margin:40px auto;padding:24px}}img{{width:120px}}a{{color:#5cdacb}}.download{{display:block;padding:16px;background:#24544f;border-radius:16px;text-align:center;text-decoration:none}}small{{color:#b9c7cc;overflow-wrap:anywhere}}</style>
<img src="/icon.svg" alt="Книга в наушниках"><h1>Audiobook {match[3]}</h1><p>{html.escape(metadata['notes'])}</p>
<a class="download" href="/updates/{match[3]}/Audiobook-Preview.apk">Скачать обновление · {metadata['size']/1048576:.1f} МБ</a>
<p>Откройте APK и подтвердите обновление в Android. Не удаляйте приложение: пакет и подпись прежние, библиотека и настройки сохраняются.</p>
<p>Первый переход на сервер ноутбука выполняется через эту ссылку. Следующие версии можно проверять в приложении через «Обновления».</p>
<small>Ноутбук должен быть включён и подключён к интернету.<br>SHA-256: {sha}</small></html>'''
    atomic_text(args.directory/'index.html', page)
    print(json.dumps({'metadata': metadata, 'apkAudit': report}, ensure_ascii=False, indent=2))

if __name__ == '__main__':
    main()
