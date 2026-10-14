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
import html
from urllib.parse import urlsplit, parse_qs, urlencode

ROOT = pathlib.Path(__file__).resolve().parents[1]
STATIC = ROOT / "src/main/resources/static"
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"


class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if urlsplit(self.path).path == "/__viewport":
            # macOS Chrome can clamp the top-level viewport to 500px. A same-origin
            # iframe guarantees the requested CSS viewport for narrow-screen tests.
            query = parse_qs(urlsplit(self.path).query)
            width = max(280, min(2000, int(query.get("width", ["390"])[0])))
            target = html.escape(query.get("page", ["/"])[0], quote=True)
            body = f'''<!doctype html><html><body style="margin:0">
<iframe title="Test viewport" style="width:{width}px;height:1000px;border:0" src="{target}"></iframe>
<script>const timer=setInterval(()=>{{
const result=document.querySelector('iframe').contentDocument?.getElementById('test-result');
if(result && /^(PASS|FAIL):/.test(result.textContent)){{
const copy=document.createElement('pre');copy.id='test-result';copy.textContent=result.textContent;
document.body.append(copy);clearInterval(timer);
}}}},20);</script></body></html>'''.encode()
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        routes = {
            "/": (STATIC / "index.html", "text/html"),
            "/app.js": (STATIC / "app.js", "text/javascript"),
            "/dashboard.html": (STATIC / "dashboard.html", "text/html"),
            "/dashboard.js": (STATIC / "dashboard.js", "text/javascript"),
            "/dashboard-ui-test.js": (ROOT / "src/test/ui/dashboard-ui.test.js", "text/javascript"),
            "/weather.html": (STATIC / "weather.html", "text/html"),
            "/weather.js": (STATIC / "weather.js", "text/javascript"),
            "/weather-ui-test.js": (ROOT / "src/test/ui/weather-ui.test.js", "text/javascript"),
            "/chat.js": (STATIC / "chat.js", "text/javascript"),
            "/chat.css": (STATIC / "chat.css", "text/css"),
            "/ui-test.js": (ROOT / "src/test/ui/chat-ui.test.js", "text/javascript"),
            "/trips.html": (STATIC / "trips.html", "text/html"),
            "/trips.js": (STATIC / "trips.js", "text/javascript"),
            "/trip-ui-test.js": (ROOT / "src/test/ui/trip-ui.test.js", "text/javascript"),
            "/location.html": (STATIC / "location.html", "text/html"),
            "/location.js": (STATIC / "location.js", "text/javascript"),
            "/location-ui-test.js": (ROOT / "src/test/ui/location-ui.test.js", "text/javascript"),
            "/itinerary.html": (STATIC / "itinerary.html", "text/html"),
            "/itinerary.js": (STATIC / "itinerary.js", "text/javascript"),
            "/itinerary-ui-test.js": (ROOT / "src/test/ui/itinerary-ui.test.js", "text/javascript"),
        }
        request_path = urlsplit(self.path).path
        if request_path not in routes:
            self.send_error(404)
            return
        path, content_type = routes[request_path]
        body = path.read_text()
        if request_path == "/":
            body = body.replace("</head>", '<script src="/ui-test.js" defer></script></head>')
        if request_path == "/trips.html":
            body = body.replace("</head>", '<script src="/trip-ui-test.js" defer></script></head>')
        if request_path == "/location.html":
            body = body.replace("</head>", '<script src="/location-ui-test.js" defer></script></head>')
        if request_path == "/itinerary.html":
            body = body.replace("</head>", '<script src="/itinerary-ui-test.js" defer></script></head>')
        if request_path == "/dashboard.html":
            body = body.replace("</head>", '<script src="/dashboard-ui-test.js" defer></script></head>')
        if request_path == "/weather.html":
            body = body.replace("</head>", '<script src="/weather-ui-test.js" defer></script></head>')
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
    page = sys.argv[3] if len(sys.argv) > 3 else "/"
    if urlsplit(page).path not in ("/", "/dashboard.html", "/weather.html", "/trips.html", "/location.html", "/itinerary.html") or not page.startswith("/") or page.startswith("//"):
        raise SystemExit("Choose a supported frontend page")
    width = int(window_size.split(",")[0])
    if width < 500:
        page = "/__viewport?" + urlencode({"width": width, "page": page})
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
                        f"http://127.0.0.1:{server.server_port}{page}",
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
