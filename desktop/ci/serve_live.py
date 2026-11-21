"""Serves a transport stream over HTTP at its own bitrate with no Content-Length, like an IPTV channel."""
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

path, duration, port = sys.argv[1], float(sys.argv[2]), int(sys.argv[3])
data = open(path, "rb").read()
rate = len(data) / duration
CHUNK = 188 * 64


class Live(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.0"

    def do_GET(self):
        print("GET", self.path, self.headers.get("User-Agent"), flush=True)
        self.send_response(200)
        self.send_header("Content-Type", "video/mp2t")
        self.end_headers()
        try:
            start = time.time()
            sent = 0
            while True:
                for i in range(0, len(data), CHUNK):
                    self.wfile.write(data[i:i + CHUNK])
                    sent += min(CHUNK, len(data) - i)
                    wait = start + sent / rate - time.time()
                    if wait > 0:
                        time.sleep(wait)
        except (BrokenPipeError, ConnectionResetError):
            pass

    def log_message(self, *args):
        pass


ThreadingHTTPServer(("127.0.0.1", port), Live).serve_forever()
