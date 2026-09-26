"""A tiny stand-in for postcodes.io, so the "Deliver to" checks don't depend on the internet.

    python3 postcodes_stub.py            # listens on 127.0.0.1:9998
    # then start the backend with LOCATION_API_BASE=http://127.0.0.1:9998
"""
import json
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import unquote, urlparse

PLACES = {
    "UB8 3PH": ("UB8", "Hillingdon"),
    "SW1A 1AA": ("SW1A", "Westminster"),
    "M1 1AE": ("M1", "Manchester"),
    "WC2N 5DU": ("WC2N", "Westminster"),
}


def result(postcode):
    outcode, district = PLACES[postcode]
    return {"postcode": postcode, "outcode": outcode, "admin_district": district, "country": "England"}


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        url = urlparse(self.path)
        if url.path.startswith("/postcodes/"):
            code = unquote(url.path[len("/postcodes/"):])
            if code in PLACES:
                return self.send(200, {"status": 200, "result": result(code)})
            return self.send(404, {"status": 404, "error": "Postcode not found"})
        if url.path == "/postcodes" and "lat=" in url.query:
            return self.send(200, {"status": 200, "result": [result("WC2N 5DU")]})
        self.send(404, {"status": 404, "error": "Resource not found"})

    def send(self, status, body):
        data = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 9998
    HTTPServer(("127.0.0.1", port), Handler).serve_forever()
