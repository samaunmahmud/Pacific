# Security

## Reporting a problem

If you find a security problem in Pacific, please **don't open a public issue**. Report it privately through
GitHub's [private vulnerability reporting](https://github.com/samaunmahmud/Pacific/security/advisories/new), or
email samaunmahmud9@gmail.com. Say what you found and how to reproduce it; you'll hear back within a week.

Please don't test against anyone else's data, and give a reasonable time to fix a problem before talking about it
publicly.

## What's in place

- **Passwords** are hashed with BCrypt. Common passwords, simple patterns and ones built from the account's email or
  name are refused. Changing or resetting a password, or "Sign out of all other devices", ends every other session.
- **Sign-in** is limited per account and per address, password reset links are single-use and stored only as hashes,
  and new customers confirm their email before ordering or selling.
- **Payments** go through Stripe Checkout, so card details never reach Pacific's servers. Webhooks must carry a valid
  Stripe signature, a payment only counts when its amount and currency match the order exactly, and anything the shop
  can't use (a late or mismatched payment) is refunded automatically and the customer is emailed.
- **Responses** carry a strict Content Security Policy, `nosniff`, no framing and a strict referrer policy; the
  storefront build adds its own Content Security Policy.
- **Production checks:** with `APP_PRODUCTION=true` the app refuses to start with the payment simulator, demo data, a
  non-https public address or localhost CORS origins.
- **Automated checks** on GitHub: tests on every change, CodeQL code scanning, Dependabot and secret scanning.

## Running it safely

- Keep secrets (`JWT_SECRET`, database and mail passwords, Stripe keys) in environment variables or `.env`, never in
  the repository. `.env` is ignored by git.
- Serve the storefront and API on the same https origin, and send `Content-Security-Policy: frame-ancestors 'none'`
  from the web server (a meta tag can't set it).
- Behind a proxy or load balancer, set `FORWARD_HEADERS_STRATEGY=framework` so sign-in limits see visitors' own
  addresses; leave it at `none` otherwise.
