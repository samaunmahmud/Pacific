import hashlib, hmac, json, sys, time, urllib.request, urllib.error, uuid

BASE, STUB = "http://localhost:8081/api", "http://127.0.0.1:9999"
SECRET = "whsec_stub_secret"
LOGFILE = sys.argv[1]


def call(method, url, token=None, body=None, raw=None, headers=None):
    data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
    req = urllib.request.Request(url, data=data, method=method)
    if data is not None and raw is None:
        req.add_header("Content-Type", "application/json")
    if raw is not None:
        req.add_header("Content-Type", "application/json")
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req) as r:
            t = r.read().decode()
            return r.status, (json.loads(t) if t else None)
    except urllib.error.HTTPError as e:
        t = e.read().decode()
        return e.code, (json.loads(t) if t else None)


api = lambda m, p, tok=None, body=None: call(m, BASE + p, tok, body)
fails = []


def check(name, cond, detail=""):
    print(("PASS " if cond else "FAIL ") + name + (f"  [{detail}]" if not cond else ""))
    if not cond:
        fails.append(name)


def stub_log():
    return [json.loads(l) for l in open(LOGFILE)]


def mode(m):
    call("POST", STUB + "/__mode?m=" + m)


def signed(payload: str):
    t = str(int(time.time()))
    sig = hmac.new(SECRET.encode(), f"{t}.{payload}".encode(), hashlib.sha256).hexdigest()
    return {"Stripe-Signature": f"t={t},v1={sig}"}


A = api("POST", "/auth/admin/login", body=dict(identifier="e2eadmin", password="e2e-admin-password"))[1]["token"]
tok = api("POST", "/auth/register", body=dict(name="Stripe Buyer", email=f"s-{uuid.uuid4().hex[:8]}@example.com", password="correct-horse-battery"))[1]["token"]
pid = api("POST", "/admin/products", A, dict(name="Stub Widget", description="d", price="30.00", stock=5))[1]["id"]
stock = lambda: api("GET", f"/products/{pid}")[1]["stock"]
addr = dict(name="B", line1="1 High St", city="Uxbridge", postcode="UB8 1AA", country="UK", paymentMethod="CARD")

check("config: card on, simulator off", api("GET", "/payments/config", tok)[1] == dict(cardEnabled=True, simulator=False))

# ---- checkout creates a Stripe session with the right request
api("POST", "/cart/items", tok, dict(productId=pid, quantity=2))
s, res = api("POST", "/orders", tok, addr)
ref = res["checkoutRef"]
check("checkout 201, provider STRIPE", s == 201 and res["payment"]["provider"] == "STRIPE", res)
check("returns Stripe's hosted url", res["payment"]["checkoutUrl"].startswith("https://checkout.stripe.com/"))
create = [l for l in stub_log() if l["path"] == "/v1/checkout/sessions" and l["method"] == "POST"][-1]
form = create["form"]
d = dict(form)
check("bearer key sent", create["auth"].startswith("Bearer sk_test"), create["auth"])
check("idempotency key = pacific-checkout-<ref>", create["idempotency"] == "pacific-checkout-" + ref, create["idempotency"])
check("mode=payment, client_reference_id=ref", d.get("mode") == "payment" and d.get("client_reference_id") == ref)
check("success/cancel urls return to /pay/return", d["success_url"].endswith("/pay/return?ref=" + ref) and d["cancel_url"].endswith("&cancelled=1"), d.get("success_url"))
check("expires_at is a future epoch", int(d["expires_at"]) > time.time() + 1500, d.get("expires_at"))
check("line item: 3000 minor units x2", d.get("line_items[0][price_data][unit_amount]") == "3000" and d.get("line_items[0][quantity]") == "2", d)
check("currency gbp", d.get("line_items[0][price_data][currency]") == "gbp")
check("metadata carries the ref", d.get("metadata[checkout_ref]") == ref and d.get("payment_intent_data[metadata][checkout_ref]") == ref)
check("customer email passed", "customer_email" in d)
check("stock reserved", stock() == 3)

# ---- return page sync with no webhook
mode("open")
check("still pending while Stripe says open", api("GET", f"/payments/{ref}", tok)[1]["status"] == "PENDING")
mode("paid")
p = api("GET", f"/payments/{ref}", tok)[1]
check("GET syncs a paid session -> PAID", p["status"] == "PAID", p)
check("order PLACED", api("GET", "/orders", tok)[1][0]["status"] == "PLACED")

# ---- real (signed) webhook after that: harmless repeat
ev = json.dumps({"id": "evt_1", "type": "checkout.session.completed", "data": {"object": {
    "id": "cs_x", "client_reference_id": ref, "payment_status": "paid", "payment_intent": "pi_test_1",
    "amount_total": 6000 + 0, "currency": "gbp"}}})
s, _ = call("POST", BASE + "/payments/webhook", raw=ev.encode(), headers=signed(ev))
check("signed webhook accepted", s == 200, s)
s, _ = call("POST", BASE + "/payments/webhook", raw=ev.encode(), headers={"Stripe-Signature": "t=1,v1=bad"})
check("bad signature rejected", s == 400, s)

# ---- cancelling the paid order refunds through Stripe with the right amount + key
oid = res["orders"][0]["id"]
check("cancel order 200", api("POST", f"/orders/{oid}/cancel", tok)[0] == 200)
refund = [l for l in stub_log() if l["path"] == "/v1/refunds"][-1]
rd = dict(refund["form"])
check("refund targets the payment intent", rd.get("payment_intent") == "pi_test_1", rd)
check("refund amount is the order total in pence", rd.get("amount") == str(int(round(res["total"] * 100))), rd)
check("refund idempotency key stable per order", refund["idempotency"] == f"pacific-refund-order-{oid}", refund["idempotency"])
check("payment shows refund", api("GET", f"/payments/{ref}", tok)[1]["refundedAmount"] == res["total"])
check("stock restored", stock() == 5)

# ---- abandon: cancelling asks Stripe to expire the session first
mode("open")
api("POST", "/cart/items", tok, dict(productId=pid, quantity=1))
s, res2 = api("POST", "/orders", tok, addr)
ref2 = res2["checkoutRef"]
check("shipping line present for a small basket", dict(next(l for l in stub_log() if l["path"] == "/v1/checkout/sessions" and dict(l["form"]).get("client_reference_id") == ref2)["form"]).get("line_items[1][price_data][unit_amount]") == "399")
s, p = api("POST", f"/payments/{ref2}/cancel", tok)
check("cancel pending -> CANCELLED", s == 200 and p["status"] == "CANCELLED", p)
check("Stripe was asked to expire the session", any(l["path"].endswith("/expire") for l in stub_log()))
check("stock released", stock() == 5)

# ---- Stripe says it expired on its side: checkout.session.expired webhook releases the stock
api("POST", "/cart/items", tok, dict(productId=pid, quantity=1))
s, res3 = api("POST", "/orders", tok, addr)
ref3 = res3["checkoutRef"]
mode("expired")
ev3 = json.dumps({"id": "evt_3", "type": "checkout.session.expired", "data": {"object": {"id": "cs_y", "client_reference_id": ref3, "payment_status": "unpaid"}}})
call("POST", BASE + "/payments/webhook", raw=ev3.encode(), headers=signed(ev3))
check("expired webhook -> EXPIRED", api("GET", f"/payments/{ref3}", tok)[1]["status"] == "EXPIRED")
check("stock released after expiry", stock() == 5)

print("\n%d failure(s)" % len(fails))
raise SystemExit(1 if fails else 0)
