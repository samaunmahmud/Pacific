import json, threading, urllib.request, urllib.error, uuid, collections

BASE = "http://localhost:8081/api"


def call(method, path, token=None, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    if data is not None:
        req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req) as r:
            text = r.read().decode()
            return r.status, (json.loads(text) if text else None)
    except urllib.error.HTTPError as e:
        text = e.read().decode()
        return e.code, (json.loads(text) if text else None)


fails = []


def check(name, cond, detail=""):
    print(("PASS " if cond else "FAIL ") + name + (f"  [{detail}]" if not cond else ""))
    if not cond:
        fails.append(name)


ADDRESS = dict(name="E2E Buyer", line1="1 High Street", city="Uxbridge", postcode="UB8 1AA", country="United Kingdom")

_, admin = call("POST", "/auth/admin/login", body=dict(identifier="e2eadmin", password="e2e-admin-password"))
A = admin["token"]


def new_customer():
    email = f"e2e-{uuid.uuid4().hex[:8]}@example.com"
    _, r = call("POST", "/auth/register", body=dict(name="E2E Buyer", email=email, password="correct-horse-battery"))
    return r["token"]


def new_product(stock, price="30.00"):
    s, p = call("POST", "/admin/products", A, dict(name="E2E Widget " + uuid.uuid4().hex[:4], description="d", price=price, stock=stock))
    assert s == 201, (s, p)
    return p["id"]


def stock(pid):
    return call("GET", f"/products/{pid}")[1]["stock"]


def card_checkout(tok, pid, qty):
    call("POST", "/cart/items", tok, dict(productId=pid, quantity=qty))
    return call("POST", "/orders", tok, dict(ADDRESS, paymentMethod="CARD"))


def orders(tok):
    return call("GET", "/orders", tok)[1]


# ---- 1. config, checkout, reservation
tok = new_customer()
check("config offers card + simulator", call("GET", "/payments/config", tok)[1] == dict(cardEnabled=True, simulator=True))
pid = new_product(5)
s, res = card_checkout(tok, pid, 2)
ref = res["checkoutRef"]
check("card checkout 201 with a payment", s == 201 and res["payment"]["status"] == "PENDING", res)
check("payment page url is the simulator", res["payment"]["checkoutUrl"].endswith("/pay/simulate/" + ref))
check("order is AWAITING_PAYMENT", res["orders"][0]["status"] == "AWAITING_PAYMENT")
check("stock reserved (5 -> 3)", stock(pid) == 3, stock(pid))

# ---- 2. pay, idempotent
s, p = call("POST", f"/payments/{ref}/simulate", tok, dict(outcome="PAID"))
check("simulate PAID -> PAID", s == 200 and p["status"] == "PAID", p)
call("POST", f"/payments/{ref}/simulate", tok, dict(outcome="PAID"))
check("order PLACED after payment", orders(tok)[0]["status"] == "PLACED")
check("stock unchanged by repeat", stock(pid) == 3)

# ---- 3. cancel paid order -> refund + restock
oid = res["orders"][0]["id"]
s, _ = call("POST", f"/orders/{oid}/cancel", tok)
pay = call("GET", f"/payments/{ref}", tok)[1]
check("cancel paid order 200", s == 200)
check("refunded exactly the order total", pay["refundedAmount"] == res["total"], pay)
check("stock restored (3 -> 5)", stock(pid) == 5, stock(pid))
check("second cancel refused", call("POST", f"/orders/{oid}/cancel", tok)[0] == 409)

# ---- 4. abandon a pending payment
s, res2 = card_checkout(tok, pid, 1)
ref2 = res2["checkoutRef"]
check("reserved again", stock(pid) == 4)
s, p = call("POST", f"/payments/{ref2}/cancel", tok)
check("cancel pending payment", s == 200 and p["status"] == "CANCELLED", p)
check("stock released", stock(pid) == 5, stock(pid))
call("POST", f"/payments/{ref2}/cancel", tok)
check("double cancel doesn't restock twice", stock(pid) == 5, stock(pid))

# ---- 5. real race: pay and cancel at the same moment, many times, with real commits
ROUNDS = 25
outcomes = collections.Counter()
inconsistent = []
pid2 = new_product(ROUNDS * 2)
for i in range(ROUNDS):
    t = new_customer()
    s, r = card_checkout(t, pid2, 1)
    rf = r["checkoutRef"]
    go = threading.Barrier(2)
    results = {}

    def pay():
        go.wait()
        results["pay"] = call("POST", f"/payments/{rf}/simulate", t, dict(outcome="PAID"))[0]

    def cancel():
        go.wait()
        results["cancel"] = call("POST", f"/payments/{rf}/cancel", t)[0]

    th = [threading.Thread(target=pay), threading.Thread(target=cancel)]
    [x.start() for x in th]
    [x.join() for x in th]
    final = call("GET", f"/payments/{rf}", t)[1]
    order_status = orders(t)[0]["status"]
    key = (final["status"], order_status, final["refundedAmount"] > 0)
    outcomes[key] += 1
    # consistent end states: paid+placed (cancel lost), cancelled+cancelled (pay lost, or refunded late)
    ok = key in {("PAID", "PLACED", False), ("CANCELLED", "CANCELLED", False), ("PAID", "CANCELLED", True)}
    if not ok:
        inconsistent.append((rf, key, results))
print("race outcomes:", dict(outcomes))
check(f"{ROUNDS} pay/cancel races all end consistent", not inconsistent, inconsistent[:3])
# stock must equal what the surviving PLACED orders imply: every cancelled/refunded order returned its unit
placed = outcomes.get(("PAID", "PLACED", False), 0)
check("stock matches surviving orders", stock(pid2) == ROUNDS * 2 - placed, f"stock={stock(pid2)} expected={ROUNDS * 2 - placed}")

# ---- 6. webhook is off without a secret; ownership
check("webhook 404 without secret", call("POST", "/payments/webhook", body={})[0] == 404)
other = new_customer()
check("other customer can't read payment", call("GET", f"/payments/{ref}", other)[0] == 404)

print("\n%d failure(s)" % len(fails))
raise SystemExit(1 if fails else 0)
