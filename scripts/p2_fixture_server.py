"""Deterministic, range-aware P2 fixture server. Run on the desktop when doing device checks.
Bind only loopback; use adb reverse tcp:8765 tcp:8765 on the user's chosen device.
"""
import argparse
import re
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

MEDIA = Path(__file__).resolve().parents[1] / "app/src/benchmark/assets/fixture.mp4"
CAPTIONS = b"WEBVTT\n\n00:00:00.000 --> 00:00:10.000\nThe quick brown fox jumps over the lazy dog.\nSecond line for subtitle editing.\n\n00:00:10.000 --> 00:01:00.000\nP2 fixture caption: timing, style, and seek check.\n"

class Handler(BaseHTTPRequestHandler):
    def do_HEAD(self):
        self.respond(False)

    def do_GET(self):
        self.respond(True)

    def respond(self, send_body):
        path = self.path.split("?", 1)[0]
        synthetic = path == "/slow/transfer.bin"
        if synthetic:
            length, kind, payload = 32 * 1024 * 1024, "application/octet-stream", None
        elif path in ("/fixture.mp4", "/slow/fixture.mp4"):
            length, kind = MEDIA.stat().st_size, "video/mp4"
            payload = None
        elif path == "/fixture.vtt":
            payload, kind = CAPTIONS, "text/vtt; charset=utf-8"
            length = len(payload)
        elif path == "/page":
            payload = b'<html><title>Fixture Series Steel Ball Run</title><video controls src="/fixture.mp4"><track src="/fixture.vtt" default></video></html>'
            length, kind = len(payload), "text/html; charset=utf-8"
        else:
            self.send_error(404)
            return
        start, end, code = 0, length - 1, 200
        requested = self.headers.get("Range")
        if requested:
            match = re.fullmatch(r"bytes=(\d*)-(\d*)", requested)
            if match and any(match.groups()):
                if match[1]:
                    start = int(match[1]); end = min(end, int(match[2])) if match[2] else end
                else:
                    start = max(0, length - int(match[2]))
                code = 206
            if not match or not any(match.groups()) or start > end or start >= length:
                self.send_response(416)
                self.send_header("Content-Range", f"bytes */{length}")
                self.end_headers()
                return
        self.send_response(code)
        self.send_header("Content-Type", kind)
        self.send_header("Content-Length", str(end - start + 1))
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("ETag", '"p2-deterministic-fixture-v1"')
        if code == 206:
            self.send_header("Content-Range", f"bytes {start}-{end}/{length}")
        self.end_headers()
        if not send_body:
            return
        try:
            if payload is not None:
                self.wfile.write(payload[start:end + 1])
            elif synthetic:
                position = start
                while position <= end:
                    size = min(8192, end - position + 1)
                    self.wfile.write(bytes((position + offset) % 256 for offset in range(size)))
                    self.wfile.flush()
                    position += size
                    time.sleep(0.05)
            else:
                with MEDIA.open("rb") as source:
                    source.seek(start)
                    remaining = end - start + 1
                    while remaining:
                        piece = source.read(min(8192, remaining))
                        if not piece: break
                        self.wfile.write(piece)
                        self.wfile.flush()
                        remaining -= len(piece)
                        if path.startswith("/slow/"): time.sleep(0.2)
        except (BrokenPipeError, ConnectionResetError):
            pass

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()
    print(f"P2 fixtures available at http://127.0.0.1:{args.port}; Ctrl+C to stop", flush=True)
    ThreadingHTTPServer(("127.0.0.1", args.port), Handler).serve_forever()
