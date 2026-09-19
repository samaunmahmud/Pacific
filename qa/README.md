# QA scripts

End-to-end checks that drive the **running** app, kept here so they survive between sessions. They are not part of
`mvn test` or `npm run build`, and they use throwaway in-memory data, never your MySQL database.

Needs: Node 20+, Python 3, Google Chrome (macOS path is hard-coded in the `.mjs` files: edit `executablePath` elsewhere).

```bash
cd qa && npm install && mkdir -p shots
```

## 1. Start a throwaway backend and dev server

Use ports 8081 and 5180 so your own app on 8080/5173 is left alone. `PUBLIC_URL` and `CORS_ALLOWED_ORIGINS` must match
the dev-server port, or the payment redirect and every POST will fail.

```bash
# terminal 1: backend with an in-memory database, the payment simulator and demo data
cd backend
PORT=8081 PUBLIC_URL=http://localhost:5180 CORS_ALLOWED_ORIGINS=http://localhost:5180 \
JWT_SECRET=e2e-secret-e2e-secret-e2e-secret-123456 PAYMENTS_SIMULATOR_ENABLED=true DEMO_DATA=true \
ADMIN_USERNAME=e2eadmin ADMIN_PASSWORD=e2e-admin-password \
DB_URL='jdbc:h2:mem:qa;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1' \
DB_USERNAME=sa DB_PASSWORD= \
mvn -o spring-boot:run -Dspring-boot.run.useTestClasspath=true \
  -Dspring-boot.run.main-class=com.pacific.marketplace.MarketplaceApplication

# terminal 2: the storefront, proxying /api to that backend
cd frontend && API_PROXY=http://localhost:8081 npx vite --port 5180 --strictPort
```

(`useTestClasspath` is only there to get the H2 driver. The `main-class` flag avoids a "multiple main classes" error
if `backend/target/test-classes` contains stray duplicate files.)

## 2. Run the checks (from `qa/`)

| Script | What it does |
| --- | --- |
| `node customer-flow.mjs` | Registers through the form, searches, filters, sorts, buys (pay on delivery, then card via the simulator, paid and abandoned), admin-cancels a paid order and checks the refund, signs out and in, and sweeps pages for console errors and failed requests. |
| `node admin-seller-review.mjs` | Admin login and pages, applying to sell and listing a product through the form, Seller Central pages, and buying, delivering and reviewing a product. |
| `node cart-controls.mjs` | Cart quantity dropdown, Save for later, Delete and Proceed to checkout (needs the demo shopper's cart: run `screenshot.mjs cart /cart` first). |
| `node screenshot.mjs <name> <path> [width] [height] [full\|empty]` | Signs in as the demo shopper (or a fresh customer) and saves `shots/<name>.png`. |
| `python3 simulator-flow.py` | Backend-only: card checkout, pay, refund, and 25 concurrent pay-versus-cancel races. |

Each script exits non-zero on any failed check, or on any browser console error or failed request.

## 3. Real-Stripe code path without a Stripe account

`stripe_stub.py` is a small stand-in for Stripe's API. Start it, then start the backend with these extra variables
instead of `PAYMENTS_SIMULATOR_ENABLED`: `STRIPE_SECRET_KEY=sk_test_stub STRIPE_API_BASE=http://127.0.0.1:9999
STRIPE_WEBHOOK_SECRET=whsec_stub_secret`.

```bash
python3 stripe_stub.py stub-requests.log &
python3 stripe-standin-flow.py stub-requests.log
```

It checks what we send to Stripe (amounts in pence, idempotency keys, return URLs, refunds) and the signed webhook.
It cannot tell you whether the real Stripe would accept those requests: do one real test-mode payment for that.
