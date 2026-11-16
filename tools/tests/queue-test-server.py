"""Synthetic slow, resumable PCM fixture. No external network or copyrighted audio."""
import argparse
import http.server
import struct
import time

parser = argparse.ArgumentParser()
parser.add_argument('--port', type=int, default=18084)
args = parser.parse_args()
size = 22050 * 2 * 20
body = (b'RIFF' + struct.pack('<I', size + 36) + b'WAVEfmt ' +
        struct.pack('<IHHIIHH', 16, 1, 1, 22050, 44100, 2, 16) +
        b'data' + struct.pack('<I', size) + bytes(size))

class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        offset = 0
        value = self.headers.get('Range', '')
        if value.startswith('bytes='):
            offset = int(value[6:].split('-')[0])
        if not 0 <= offset < len(body):
            self.send_error(416)
            return
        self.send_response(206 if offset else 200)
        self.send_header('Content-Type', 'audio/wav')
        self.send_header('Accept-Ranges', 'bytes')
        self.send_header('ETag', '"stage3-fixture"')
        self.send_header('Content-Length', str(len(body) - offset))
        if offset:
            self.send_header('Content-Range', f'bytes {offset}-{len(body)-1}/{len(body)}')
        self.end_headers()
        print(f'request offset={offset}', flush=True)
        try:
            for start in range(offset, len(body), 4096):
                self.wfile.write(body[start:start + 4096])
                self.wfile.flush()
                time.sleep(.1)
        except (BrokenPipeError, ConnectionResetError):
            pass
    def log_message(self, *_):
        pass

http.server.ThreadingHTTPServer(('127.0.0.1', args.port), Handler).serve_forever()
