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

**Customers** — register/sign in · browse, search, filter, sort · deals · wish list · cart (saved on the server) ·
checkout · order history and cancellation while an order is still "Placed" · reviews (5-minute edit window,
`app.reviews.edit-window-minutes`), helpful/unhelpful votes, reporting · "unrated purchases" dashboard.

**Admins** — dashboard (gross sales, commission earned, sellers, review pie chart, ratings, low stock) · sellers,
commission and payouts · moderation queue · Pacific's own products, categories and stock · all orders.

**Rules worth knowing**
- Only people who bought a product (non-cancelled order) can review it, once each. You can't buy your own product.
- A reported review is hidden from the product page (its author still sees it) until an admin decides.
- Payment is **pay on delivery** — there is no online payment integration yet.
- Products are hidden, never hard-deleted, because past orders refer to them.

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
cd backend && mvn test        # 36 integration tests (H2 in MySQL mode): auth, stock/checkout, reviews, import, marketplace
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

Online payments and real seller payouts (the ledger is bookkeeping only), email notifications (order confirmations,
seller approval, password reset), product image upload (use an image URL for now), refunds/returns, seller-to-buyer
messaging, and rate-limiting on login.
