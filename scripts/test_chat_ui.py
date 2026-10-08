"""Run deterministic UI checks in installed headless Chrome, without Node or providers."""
import http.server
import pathlib
import subprocess
import tempfile
import threading
import sys
import os
import re
import signal
import time

ROOT = pathlib.Path(__file__).resolve().parents[1]
STATIC = ROOT / "src/main/resources/static"
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"


class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        routes = {
            "/": (STATIC / "index.html", "text/html"),
            "/chat.js": (STATIC / "chat.js", "text/javascript"),
            "/chat.css": (STATIC / "chat.css", "text/css"),
            "/ui-test.js": (ROOT / "src/test/ui/chat-ui.test.js", "text/javascript"),
        }
        if self.path not in routes:
            self.send_error(404)
            return
        path, content_type = routes[self.path]
        body = path.read_text()
        if self.path == "/":
            body = body.replace("</head>", '<script src="/ui-test.js" defer></script></head>')
        data = body.encode()
        self.send_response(200)
        self.send_header("Content-Type", content_type + "; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    chrome = sys.argv[1] if len(sys.argv) > 1 else CHROME
    window_size = sys.argv[2] if len(sys.argv) > 2 else "1280,1000"
    with http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler) as server:
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            with tempfile.TemporaryDirectory(prefix="travel-ui-browser-") as profile:
                # Chrome helpers can retain stdout pipes after DOM capture on macOS.
                # Read the completed report from a temporary file and own only this process group.
                with tempfile.TemporaryFile(mode="w+") as output:
                    process = subprocess.Popen([
                        chrome, "--headless", "--disable-gpu", "--no-first-run", "--no-default-browser-check",
                        "--disable-background-networking", f"--user-data-dir={profile}",
                        f"--window-size={window_size}",
                        "--dump-dom", "--timeout=15000", "--virtual-time-budget=5000",
                        f"http://127.0.0.1:{server.server_port}/",
                    ], stdout=output, stderr=subprocess.DEVNULL, start_new_session=True)
                    match = None
                    try:
                        deadline = time.monotonic() + 40
                        while time.monotonic() < deadline:
                            output.seek(0)
                            match = re.search(r'<pre id="test-result">([^<]+)</pre>', output.read())
                            if match or process.poll() is not None:
                                break
                            time.sleep(0.1)
                    finally:
                        if process.poll() is None:
                            os.killpg(process.pid, signal.SIGTERM)
                            try:
                                process.wait(timeout=3)
                            except subprocess.TimeoutExpired:
                                os.killpg(process.pid, signal.SIGKILL)
                                process.wait(timeout=3)
                    if not match or not match[1].startswith("PASS:"):
                        raise SystemExit(match[1] if match else "FAIL: browser did not finish UI checks")
                    print(match[1])
        finally:
            server.shutdown()
