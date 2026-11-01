#!/usr/bin/env python3
"""Serve only public update artifacts on loopback, behind HTTPS Funnel."""
import argparse
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import re
from urllib.parse import urlsplit

class Updates(SimpleHTTPRequestHandler):
    extensions_map = {**SimpleHTTPRequestHandler.extensions_map,
                      '.apk': 'application/vnd.android.package-archive'}

    def send_head(self):
        path = urlsplit(self.path).path
        allowed = path in ('/', '/index.html', '/icon.svg',
                          '/updates/preview-update.json', '/updates/Audiobook-Preview.apk')
        allowed |= bool(re.fullmatch(r'/updates/4\.2\.[1-9][0-9]*/Audiobook-Preview\.apk', path))
        root = Path(self.directory).resolve()
        target = Path(self.translate_path(path)).resolve()
        if not allowed or not target.is_relative_to(root):
            self.send_error(404)
            return None
        return super().send_head()

    def list_directory(self, path):
        self.send_error(404)
        return None

    def end_headers(self):
        self.send_header('Cache-Control', 'no-store')
        self.send_header('X-Content-Type-Options', 'nosniff')
        super().end_headers()

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', required=True, type=Path)
    parser.add_argument('--port', type=int, default=8787)
    args = parser.parse_args()
    if not args.directory.is_dir():
        parser.error('Public update directory does not exist')
    server = ThreadingHTTPServer(('127.0.0.1', args.port),
                                partial(Updates, directory=str(args.directory.resolve())))
    print(f'Public APK server listening on 127.0.0.1:{args.port}', flush=True)
    server.serve_forever()

if __name__ == '__main__':
    main()
