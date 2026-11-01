"""Check that the public server serves APK bytes but never neighboring private files."""
import functools
from http.client import HTTPConnection
from http.server import ThreadingHTTPServer
from pathlib import Path
import runpy
import tempfile
import threading
import unittest

handler = runpy.run_path(str(Path(__file__).resolve().parents[1]/'laptop-update-server.py'))['Updates']

class PublicServerTest(unittest.TestCase):
    def test_download_and_private_path_isolation(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)/'public'
            (root/'updates/4.2.89').mkdir(parents=True)
            (root/'updates/4.2.89/Audiobook-Preview.apk').write_bytes(b'APK-test-bytes')
            (root/'updates/preview-update.json').write_text('{"versionCode":100089}')
            (root/'index.html').write_text('Audiobook')
            (Path(folder)/'preview.keystore').write_bytes(b'PRIVATE')
            (root/'accidental-secret.txt').write_text('PRIVATE')
            server = ThreadingHTTPServer(('127.0.0.1', 0), functools.partial(handler, directory=str(root)))
            worker = threading.Thread(target=server.serve_forever, daemon=True)
            worker.start()
            def request(path, method='GET'):
                connection = HTTPConnection(*server.server_address, timeout=5)
                connection.request(method, path)
                response = connection.getresponse()
                result = response.status, {k.lower(): v for k, v in response.getheaders()}, response.read()
                connection.close()
                return result
            try:
                status, headers, body = request('/updates/4.2.89/Audiobook-Preview.apk')
                self.assertEqual((status, body), (200, b'APK-test-bytes'))
                self.assertEqual(headers['content-type'], 'application/vnd.android.package-archive')
                self.assertEqual(int(headers['content-length']), len(body))
                self.assertEqual(request('/updates/preview-update.json')[0], 200)
                self.assertEqual(request('/')[0], 200)
                self.assertEqual(request('/updates/4.2.89/Audiobook-Preview.apk', 'HEAD')[2], b'')
                for path in ['/updates/', '/preview.keystore', '/../preview.keystore', '/%2e%2e/preview.keystore',
                             '/accidental-secret.txt', '/updates/4.2.89/../../preview.keystore', '/.git/config', '/updates/missing.apk']:
                    with self.subTest(path=path):
                        status, _, body = request(path)
                        self.assertEqual(status, 404)
                        self.assertNotIn(b'PRIVATE', body)
                self.assertEqual(request('/', 'POST')[0], 501)
            finally:
                server.shutdown()
                server.server_close()
                worker.join()

if __name__ == '__main__':
    unittest.main()
