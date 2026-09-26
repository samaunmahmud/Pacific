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

New customers must confirm their email before ordering or selling, so scripts that register customers confirm them
through the admin endpoint (`confirm.mjs`); only `verification.mjs` uses the real link.

| Script | What it does |
| --- | --- |
| `node customer-flow.mjs` | Registers through the form, searches, filters, sorts, buys (pay on delivery, then card via the simulator, paid and abandoned), admin-cancels a paid order and checks the refund, signs out and in, and sweeps pages for console errors and failed requests. |
| `node admin-seller-review.mjs` | Admin login and pages, applying to sell and listing a product through the form, Seller Central pages, and buying, delivering and reviewing a product. |
| `node cart-controls.mjs` | Cart quantity dropdown, Save for later and Move to cart, Delete and Proceed to checkout (needs the demo shopper's cart: run `screenshot.mjs cart /cart` first). |
| `node abandoned-payment.mjs` | Checks out by card, walks away from the payment page, cancels from the return page, and checks the cart is back with the right quantities; a paid checkout still leaves the cart empty. Needs the cart-restore change (PR #5). |
| `node lifecycle.mjs` | Buyer checks out; the seller ships it with a carrier and tracking number through the form; the buyer sees the timeline and a tracking link; an admin reads the emails the shop sent. Needs the order timeline and emails change. |
| `node returns.mjs` | A card order is delivered; the buyer asks to return one item; the seller approves and refunds it (an over-limit amount is refused); checks the payment, the seller's earnings, and the buyer's view; a second request is declined with a reason; an admin sees every return. Needs the returns change. |
| `BACKEND_LOG=<file> node account.mjs` | Forgot password through the UI (the dev-only reset link is read from the backend's output, so start it with its output going to `<file>`), the reset page, old sessions and links dying, the admin log hiding the link, changing name and password, the address book, checkout with a saved address, and the sign-in lockout. Needs the account security change. |
| `node photos.mjs` | A seller uploads a photo through the product form (checks it's resized to 1600 px and shown on the product page and catalogue), a file that isn't a photo gets a clear message, the photo is removed and replaced by a link, the form fits a phone screen, and an admin saves a demo product without losing its drawing. Needs `DEMO_DATA=true`. |
| `node gallery.mjs` | A seller adds three more photos at once, reorders, removes one and makes another the main photo; shoppers switch photos on the product page by clicking thumbnails and with the arrow keys; a one-photo product has no thumbnails; choosing too many keeps seven more and says so; phone layouts fit. |
| `BACKEND_LOG=<file> node verification.mjs` | Sign up through the form, the confirm-your-email reminder, checkout and "Sell on Pacific" waiting for it, "send it again" and its one-a-minute limit, opening the link (read from the backend output, like `account.mjs`) twice and a broken one, ordering afterwards, confirming on another device, the admin log hiding the link, an admin confirming by hand, and the phone layout. |
| `node messaging.mjs` | A buyer writes to a store from a product page, the seller's unread badge, reading and replying (Ctrl+Enter), the buyer's badge and the reply, a seller writing to the buyer of an order in the same conversation, no message link on your own products, another customer refused, the notification email not quoting the message, and phone layouts. |
| `node offers.mjs` | A second store joins a product page with "Sell on Pacific" and wins the buy box with a cheaper offer, search shows one card that buys from the buy box, a shopper adds another seller's offer, the buy box moves when the winner sells out, a seller edits their offer's terms (not the product's details) and a used offer doesn't take the buy box from a new one, phone layout. |
| `node delivery.mjs` | A seller sets their own free-delivery amount and dispatch time; the product page promises standard and express dates; the cart shows them; choosing express at checkout updates the total and the order page shows the promised arrival; phone layout. |
| `node promotions.mjs` | A seller starts a Lightning Deal, a coupon and a promo code from Seller Central; the shopper sees the deal on the card and buy box, clips the coupon, sees both in the cart, has a bad code refused and a good one take 15% off at checkout; the order shows each line's promotion and Seller Central counts the uses; phone layout. |
| `node discovery.mjs` | Search suggestions with the keyboard, words in any order ranked by best match, "Frequently bought together" with add-all, related products, "Buy it again" on the home page and a delivered order, phone layout. |
| `node variations.mjs` | A seller sets up colours and sizes on the product form and adds three variations (a repeated one is refused); search shows one card with "See options"; the picker switches size and colour (keeping the size), shows a sold-out option and crosses out a missing one; the cart says which variation; demo clothes show a picture per colour; phone layouts. Needs `DEMO_DATA=true`. |
| `node deliver-to.mjs` | "Deliver to": a visitor's wrong and unknown postcodes are refused, a real one is named in the top bar and kept after reloading, "Use my current location" finds the nearest postcode; a customer sees their default address, picks another, sees it in the buy box and at checkout; a deleted address falls back; signing out hides it; phone strip and dialog. Needs the backend started with `LOCATION_API_BASE=http://127.0.0.1:9998` and `python3 postcodes_stub.py` running. |
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
