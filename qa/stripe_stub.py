"""A stand-in for Stripe's REST API, just enough for StripeGateway. Logs what the app sends to stub-requests.log."""
import json, sys, urllib.parse
from http.server import BaseHTTPRequestHandler, HTTPServer

STATE = {"mode": "open"}  # open | paid | expired  (what GET session reports)
LOG = open(sys.argv[1], "a", buffering=1)


def session(sid, status, pay, pi=None):
    return {"id": sid, "object": "checkout.session", "status": status, "payment_status": pay, "payment_intent": pi,
            "url": f"https://checkout.stripe.com/c/pay/{sid}", "livemode": False}


class H(BaseHTTPRequestHandler):
    def _send(self, obj, code=200):
        body = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _body(self):
        n = int(self.headers.get("Content-Length") or 0)
        return urllib.parse.parse_qsl(self.rfile.read(n).decode()) if n else []

    def log_message(self, *a):
        pass

    def do_POST(self):
        path = self.path.split("?")[0]
        if path == "/__mode":
            STATE["mode"] = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)["m"][0]
            return self._send({"ok": True})
        form = self._body()
        LOG.write(json.dumps({"method": "POST", "path": path, "idempotency": self.headers.get("Idempotency-Key"),
                              "auth": (self.headers.get("Authorization") or "")[:14], "form": form}) + "\n")
        if path == "/v1/checkout/sessions":
            ref = dict(form).get("client_reference_id", "x")
            return self._send(session("cs_test_" + ref[:8], "open", "unpaid"))
        if path.startswith("/v1/checkout/sessions/") and path.endswith("/expire"):
            return self._send(session(path.split("/")[4], "expired", "unpaid"))
        if path == "/v1/refunds":
            return self._send({"id": "re_test_1", "object": "refund", "amount": int(dict(form)["amount"]), "status": "succeeded"})
        self._send({"error": {"message": "not stubbed: " + path}}, 404)

    def do_GET(self):
        path = self.path.split("?")[0]
        LOG.write(json.dumps({"method": "GET", "path": path, "mode": STATE["mode"]}) + "\n")
        if path.startswith("/v1/checkout/sessions/"):
            sid = path.split("/")[4]
            m = STATE["mode"]
            if m == "paid":
                return self._send(session(sid, "complete", "paid", "pi_test_1"))
            if m == "expired":
                return self._send(session(sid, "expired", "unpaid"))
            return self._send(session(sid, "open", "unpaid"))
        self._send({"error": {"message": "not stubbed"}}, 404)


HTTPServer(("127.0.0.1", 9999), H).serve_forever()
