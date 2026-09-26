# Pacific — multi-seller marketplace

Spring Boot API + React storefront, with the original Pacific look (maroon `#860752`, blush `#f9dcd1`, Georgia logo,
pill search bar, rounded cards). It grew out of the JavaFX "Pacific Reviews" app: reviews, ratings, votes and
moderation are all still here, now inside a marketplace where independent sellers list and ship their own products.

```
backend/    Spring Boot 3.5 · Java 21 · JPA · Flyway · MySQL · JWT (BCrypt passwords)
frontend/   React 19 · TypeScript · Vite   (dev server proxies /api to :8080)
```

## How the marketplace works

- **Sellers are ordinary customer accounts.** Anyone can apply at **/sell** with a store name. An admin approves the
  application (Admin → Sellers), and the account then gets **Seller Central** at `/seller`. Admins can also reject,
  suspend (the seller's products vanish from the store instantly) and reinstate.
- **Products belong to a seller.** Your original 24 products are "Sold by Pacific" (the house store, run by admins).
  Sellers manage only their own listings, stock, deals and orders; they can't see or touch anyone else's.
- **One basket, many sellers.** Checkout is split into **one order per seller**, each with its own shipping (flat £3.99,
  free from £50 *per seller*) and its own status. Sellers ship and update only their own orders.
- **Commission & payouts (bookkeeping only — no real money moves).** When a seller's order is **delivered** they are
  credited the sale (items + shipping) and debited the marketplace commission (on the item subtotal). The commission
  rate is snapshotted on each order when it's placed, so changing the rate later never rewrites history. The default
  rate (10%) and per-seller overrides are set by admins, who also record payouts against a seller's balance
  (never more than the balance, even with two admins clicking at once).
- **Deals:** give a product a higher "was" price and it appears on **Today's Deals** with a discount badge.
- **Public seller storefronts** (`/sellers/<slug>`) with a "Sold by …" link on every product, plus **seller ratings**:
  buyers rate a seller separately from the product, once an order from them has been delivered.
- **Product Q&A:** signed-in customers ask; the seller, Pacific (admins) or people who bought the product answer, and
  each answer is labelled. Admins can delete anything; authors can delete their own.
- **Wish list** (saved on the server) and **Recently viewed** (kept in the visitor's own browser).
- **Pay by card (optional).** At checkout customers can pay on delivery or by card through Stripe Checkout (see
  [Card payments](#card-payments)). One card payment covers every seller's order in that checkout.

**Customers** — register/sign in · browse, search, filter, sort · deals · wish list · cart (saved on the server) ·
checkout · order history and cancellation while an order is still "Placed" · reviews (5-minute edit window,
`app.reviews.edit-window-minutes`), helpful/unhelpful votes, reporting · "unrated purchases" dashboard.

**Admins** — dashboard (gross sales, commission earned, sellers, review pie chart, ratings, low stock) · sellers,
commission and payouts · moderation queue · Pacific's own products, categories and stock · all orders.

**Rules worth knowing**
- Only people who bought a product (non-cancelled order) can review it, once each. You can't buy your own product.
- A reported review is hidden from the product page (its author still sees it) until an admin decides.
- Payment is **pay on delivery**, or **by card** when Stripe (or the local simulator) is configured.
- Products are hidden, never hard-deleted, because past orders refer to them.
- New customers **confirm their email** with the link sent at sign-up (valid 48 hours, `app.security.verify-token-hours`)
  before they can order, apply to sell or post questions and answers; browsing, the cart and the wish list work
  straight away. Accounts from before this rule, demo accounts and imported ones count as confirmed. The admin
  Emails page can confirm a customer by hand if the email never arrives. Without a mail server, the link is written
  to the backend's output in development (never with `APP_PRODUCTION=true`) and hidden in the admin email log.

**Promotions** — each store runs, and pays for, its own (Seller Central → Promotions; admins run them on Pacific's own
products under Admin → Promotions):
- **Lightning Deals**: a deal price for 1–12 hours on a set number of units. The card and buy box show the deal, a
  countdown and how much has been claimed; while it runs, the deal price competes for the buy box.
- **Coupons**: "Save 10% with coupon" on a listing. Shoppers tick "Apply coupon"; each customer can use it once, up to
  the store's budget of uses.
- **Promo codes**: typed at checkout for a percentage off that store's items (optional minimum spend and use limit),
  once per customer. A line gets the better of its deal and coupon, and a code stacks on top.

Every promotion lowers the price paid per unit, so orders, refunds, returns and seller earnings need nothing special.
Checkout claims deal units, coupon uses and code uses with atomic updates (they can't be oversold), and a cancelled
order gives them back. "Today's Deals" includes products with a Lightning Deal or coupon. Demo shops get coupons, the
code WELCOME10 (10% off Pacific's products) and a rolling batch of Lightning Deals.

**Delivery** — each seller's part of an order can go **Standard** (free once that seller's part reaches their
free-delivery amount, otherwise £3.99) or **Express** (£5.99, faster), chosen per seller at checkout. Dates are
promised everywhere: the buy box ("FREE delivery Thu 1 Oct – Fri 2 Oct … Order within 3 hrs 12 mins"), the cart,
checkout, the confirmation email and the order page ("Arriving …"). They count business days: orders before 2pm on
a weekday count from that day, then the seller's dispatch time (set in Seller Central → Store settings, with their own
free-delivery amount), then 2–3 days standard or 1 day express. Prices, days and the cut-off are in `app.shop`.
**Save for later** keeps items in the cart but out of its total and of checkout, until moved back.

**Several sellers per product** — like Amazon, a product has one page however many stores sell it. An approved
seller can "Sell on Pacific" from any product page to add their own offer (price, stock and condition: new or used)
instead of creating a duplicate listing. The **buy box** goes to the best offer on sale: in stock beats out of stock,
new beats used, then the lowest price, then whoever listed first; the rest appear under "Other sellers on Pacific".
Search shows one card per product at the buy-box price, and "Add to cart" there buys the buy-box offer. When the
winner sells out, is hidden or its store is suspended, the next offer takes over (and the product stays on sale while
anyone sells it). Reviews and questions live on the product page, so a buyer from any seller can review it. An offer's
name, photos and description follow the product page; its seller sets only their price, stock, condition and
visibility. Each offer is still its own listing, so the cart, orders, stock and seller earnings work as before.

**Deliver to** — the top bar shows where orders go. Signed-in customers see their default address ("Deliver to
Casey · Uxbridge UB8 3PH") and can pick another saved address; anyone can type a UK postcode or use their device's
location. Checkout starts with the chosen address (or pre-fills the postcode) and the buy box repeats it. Postcodes are
looked up through the shop with [postcodes.io](https://postcodes.io) (free, no key; `LOCATION_API_BASE` to change it),
cached and limited to 120 lookups an hour per visitor. If the service can't be reached, a well-formed postcode is still
accepted without its place name. The choice is kept in the browser, and a customer's address is never shown after they
sign out.

**Variations** — a product can come in other colours, sizes and so on (one or two things it varies by, e.g. Colour
and Size). On a product's edit page (Seller Central, or Admin for Pacific's own) the store says what the variations
differ by and adds them, each with its own price, stock and optionally its own photo. Search shows the family as one
card ("4 options available", "See options"), standing in for the first variation on sale; words naming any variation
("blue") find it. The product page has a picker: choosing a colour keeps the chosen size when that combination exists,
sold-out options are dashed and missing combinations crossed out. Each variation is its own product page, so other
sellers can offer it, and it has its own reviews, stock and buy box; the cart and orders say which one was bought
("Colour: Blue, Size: L", kept on the order even if the store renames it later). Demo shops get some clothes in three
colours and three sizes, and some shoes, hats and headphones in three colours.

**Messages** — buyers can write privately to a store from a product page or one of their orders ("Message the
seller"), and sellers can write to the buyer of one of their orders; each side has an inbox (Your Messages, and
Messages in Seller Central) with unread badges. There's one conversation per buyer and store, each message can say
which product or order it's about, and only the two sides can read it (not other customers, not admins). The other
side is emailed when a conversation gets its first unread message, not for every message, and the email doesn't quote
it, since the admin email log keeps a copy. Suspended stores can't be messaged (the history stays readable); sending is
limited to 60 messages an hour per account.

**Product photos** — sellers and admins upload a photo on the product form (or drop one on it, or paste a link to a
photo hosted elsewhere). The shop checks it really is a JPEG, PNG or GIF (up to 10 MB), turns sideways phone photos
the right way up, and saves a fresh copy at most 1600 px on its longer side, which also removes hidden data such as
where the photo was taken. Photos are kept in `UPLOADS_DIR` (default `backend/uploads/`): on a real server put it on
a disk that survives redeploys and include it in backups. Uploads are limited to 60 per account per hour.

A product can have **up to eight photos**: the main one (shown on cards, in the cart and on orders) and up to seven
more, added several at a time. Sellers can reorder them or make any of them the main photo; shoppers switch between
them on the product page with the thumbnails or the arrow keys.

## Card payments

Card details are entered on Stripe's own page and never reach this server. Without any payment settings the shop is
pay-on-delivery only.

**How a card order flows**
1. Checkout with "Pay by card now" creates the orders as **Awaiting payment**: stock is reserved, but sellers can't
   see them and they don't count as sales. The customer is sent to Stripe.
2. When Stripe confirms payment (webhook, or the customer returning to `/pay/return`) the orders become **Placed**.
3. If the customer doesn't pay within `app.payments.pending-expiry-minutes` (35; Stripe needs at least 30) or cancels,
   the orders are cancelled and the stock goes back on sale. A background job does this every minute.
4. A payment that arrives after that (paid at the last moment) is **refunded in full** automatically.
5. Cancelling a paid card order (customer, seller or admin) **refunds that order's total** to the card. In a
   multi-seller checkout only the cancelled order is refunded.

**Turn it on** (set as environment variables; never commit keys):
- `STRIPE_SECRET_KEY` — use a test key (`sk_test_…`) first. `PUBLIC_URL` must be where the storefront is served from.
- `STRIPE_WEBHOOK_SECRET` — add a webhook endpoint in the Stripe dashboard pointing at
  `https://<your-api-host>/api/payments/webhook` with the events `checkout.session.completed`,
  `checkout.session.async_payment_succeeded` and `checkout.session.expired`, and copy its signing secret. Locally:
  `stripe listen --forward-to localhost:8080/api/payments/webhook` prints one. Requests without a valid signature are
  rejected, and the amount and currency must match the order.
- No Stripe account yet? Set `PAYMENTS_SIMULATOR_ENABLED=true` for a fake "payment page" with Pay and Cancel buttons.
  It moves no money and is refused alongside a live Stripe key.

## Demo data (development only)

An empty shop looks empty. Start the API with `DEMO_DATA=true` to fill it with **about 2,150 invented products** in 13
categories from 20 stores, 41 shoppers and about 21,000 reviews, with deals, coupons, other sellers' offers, colours
and sizes, low stock and back-dated timestamps. The first 154 products come first; the larger catalogue (2,000 more,
`DEMO_EXTRA_PRODUCTS` to change or `0` for none) is added on top once, including to demo databases made before it,
and takes about 15 seconds on MySQL.
Product pictures are drawn in the browser, so nothing is downloaded. It only *adds* data, runs once per database, and
leaves your own products, users and orders alone. All brands and people are made up. It creates one account you can
sign in with, `demo.shopper@example.com` / `Demo-Pacific-123`, so **never enable it against a real shop**.

## Run it locally

Prerequisites: JDK 21, Maven, Node 20+, MySQL 8.

1. **Create the database** (once). Edit the password in the script first:
   ```bash
   mysql -uroot -p -h127.0.0.1 -P3307 < backend/src/main/resources/db/setup-mysql.sql
   ```
2. **Configure** — copy `.env.example` to `.env`, fill in `JWT_SECRET` and `DB_PASSWORD`.
3. **Start the API** (Flyway creates the tables on first start):
   ```bash
   set -a; source .env; set +a
   cd backend && mvn spring-boot:run
   ```
4. **Start the storefront**:
   ```bash
   cd frontend && npm install && npm run dev      # http://localhost:5173
   ```

### Import the old JavaFX data (optional, once)

With an **empty** database, start the API with `LEGACY_SQLITE_PATH=backend/legacy-import/DataBase1.db`. It imports
24 products, 23 customers, 2 admins and 76 reviews, assigns categories, gives each product 50 in stock, and
**BCrypt-hashes the old plaintext passwords** (the old app stored them unhashed). Existing logins keep working.
The importer refuses to run if the database already has data.

On a fresh install with no import, set `ADMIN_USERNAME` / `ADMIN_PASSWORD` to create the first admin.

Sign-in pages: customers at `/login`, admins at `/admin/login` (an account only works through its own portal).

## Tests

```bash
cd backend && mvn test        # 74 integration tests (H2 in MySQL mode): auth, stock/checkout, reviews, import, marketplace, card payments
cd frontend && npm run typecheck && npm run build
```

## What changed from the JavaFX app

- Passwords are now hashed; the login is a JWT bearer token (12 h).
- Helpful/unhelpful votes are stored per user (previously anyone could click repeatedly and the `vote` table was unused).
- The edit window counts from posting time; editing no longer restarts it.
- The old "prohibited characters" rule (which rejected any full stop or apostrophe) is gone; comments are limited to
  500 characters instead. The unused "public name" and "rate 1–10" fields were dropped, and the media box became an
  optional photo URL.
- Reviews now have a separate title (the old app glued it onto the comment as `Title: body`).

## Not built yet

Real seller payouts (the ledger is bookkeeping only), changing the email address on an account,
cleaning up photos no product uses any more, cloud storage for photos, and reporting or blocking in messages
(message attachments, too).
