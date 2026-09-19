// End-to-end QA in a real browser. Buys things by clicking, and records every console error, uncaught exception and
// failed request on every page visited.
import puppeteer from 'puppeteer-core';

const BASE = 'http://localhost:5180';
const OUT = new URL('./shots/', import.meta.url).pathname;
const API = BASE + '/api';
const results = [];
const problems = [];
let where = 'start';

const check = (name, ok, detail = '') => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + name + (ok ? '' : `  [${detail}]`)); };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
async function call(method, url, token, body) {
  const r = await fetch(API + url, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) }, body: body ? JSON.stringify(body) : undefined });
  const t = await r.text();
  return t ? JSON.parse(t) : null;
}

const browser = await puppeteer.launch({ executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: 'new', args: ['--hide-scrollbars'] });
const page = await browser.newPage();
await page.setViewport({ width: 1440, height: 900 });
page.on('dialog', (d) => d.accept());
page.on('pageerror', (e) => problems.push(`[${where}] uncaught: ${e.message}`));
let expectingRejection = false;
page.on('console', (m) => { if (m.type() === 'error' && !(expectingRejection && m.text().includes('401'))) problems.push(`[${where}] console.error: ${m.text().slice(0, 160)}`); });
page.on('response', (r) => {
  const u = r.url();
  if (u.startsWith(BASE) && r.status() >= 400 && !(expectingRejection && r.status() === 401) && !u.includes('/@vite') && !r.request().url().endsWith('favicon.ico')) problems.push(`[${where}] HTTP ${r.status()} ${r.request().method()} ${u.replace(BASE, '')}`);
});

async function go(path, label = path) {
  where = label;
  await page.goto(BASE + path, { waitUntil: 'networkidle0' });
  await sleep(300);
}
async function shot(name) { await page.screenshot({ path: `${OUT}${name}.png`, fullPage: true }); }
const text = (sel) => page.$eval(sel, (e) => e.textContent).catch(() => '');
async function clickText(sel, contains) {
  const handles = await page.$$(sel);
  for (const h of handles) if ((await h.evaluate((e) => e.textContent)).includes(contains)) { await h.click(); return true; }
  return false;
}

// ---------- 1. register through the form ----------
const email = `qa${Date.now()}@example.com`;
await go('/register');
await page.type('input[aria-label="Full name"]', 'Quinn Tester');
await page.type('input[aria-label="Email"]', email);
await page.type('input[aria-label="Password"]', 'correct-horse-battery');
await page.click('.login-button');
await page.waitForFunction(() => location.pathname === '/', { timeout: 8000 }).catch(() => {});
check('register form signs the new customer in and lands on the home page', new URL(page.url()).pathname === '/', page.url());
await sleep(500);
check('header greets them by name', (await text('.header-actions')).includes('Quinn'));

// ---------- 2. browse: header search with category, filter, sort, product page ----------
await page.select('.search-cat', 'audio');
await page.type('.transparent-search', 'headphones');
await page.keyboard.press('Enter');
await page.waitForFunction(() => location.pathname === '/products', { timeout: 8000 }).catch(() => {});
await sleep(800);
check('search + category goes to filtered results', page.url().includes('q=headphones') && page.url().includes('category=audio'), page.url());
const cards = await page.$$('.product-card');
check('search returns headphones only', cards.length > 0 && (await Promise.all(cards.map((c) => c.$eval('.card-title', (e) => e.textContent.toLowerCase())))).every((t) => t.includes('headphone')), `${cards.length} cards`);

await go('/products?category=fashion');
await clickText('.filters li button', '£25 to £50');
await sleep(800);
const prices = await page.$$eval('.product-card .money-big', (els) => els.map((e) => Number(e.getAttribute('aria-label').replace(/[^0-9.]/g, ''))));
check('price band filter only shows £25–£50 products', prices.length > 0 && prices.every((p) => p >= 25 && p <= 50), JSON.stringify(prices));
await page.select('.sort-by select', 'price_asc');
await sleep(800);
const sorted = await page.$$eval('.product-card .money-big', (els) => els.map((e) => Number(e.getAttribute('aria-label').replace(/[^0-9.]/g, ''))));
check('sort by price low to high', sorted.every((p, i) => i === 0 || p >= sorted[i - 1]), JSON.stringify(sorted));

// ---------- 3. product page -> buy box -> cart ----------
const productPath = await page.$eval('.product-card', (a) => a.getAttribute('href'));
await go(productPath, 'product');
await page.click('.buy-box .cart-btn');
await sleep(800);
check('Add to cart from the buy box updates the header count', (await text('.cart-badge')).trim() === '1', await text('.cart-badge'));
await go('/cart');
check('cart shows the item and a checkout button', (await page.$$('.cart-item')).length === 1 && (await page.$('.cart-summary .cart-btn')) !== null);

// ---------- 4. checkout: validation, then pay on delivery ----------
await page.click('.cart-summary .cart-btn');
await page.waitForSelector('#name', { timeout: 8000 });
await page.click('form .submit-btn'); // empty form: the browser's own validation must stop it
await sleep(400);
check('empty checkout form is not submitted', new URL(page.url()).pathname === '/checkout');
async function fillAddress() {
  await page.waitForSelector('#name', { timeout: 8000 });
  await page.$eval('#name', (e) => (e.value = ''));
  await page.type('#name', 'Quinn Tester');
  await page.type('#line1', '10 Test Street');
  await page.type('#city', 'Uxbridge');
  await page.type('#postcode', 'UB8 1AA');
}
await fillAddress();
await page.click('form .submit-btn');
await page.waitForFunction(() => /^\/orders\/\d+$/.test(location.pathname), { timeout: 8000 }).catch(() => {});
where = 'order detail (COD)';
await sleep(600);
check('pay-on-delivery checkout lands on the order page', /^\/orders\/\d+$/.test(new URL(page.url()).pathname), page.url());
check('...which says the order is placed', (await text('.orders-page')).includes('Order placed') && (await text('.orders-page')).includes('pay on delivery'.replace('pay', 'pay')) || (await text('.orders-page')).includes('Pay on delivery'));
check('...and shows the progress tracker', (await page.$('.tracker')) !== null);
await shot('qa-order-cod');
check('cart is empty afterwards', (await text('.cart-badge')).trim() === '0');

// ---------- 5. card payment via the simulator: pay ----------
await go(productPath, 'product');
await page.click('.buy-box .cart-btn');
await sleep(600);
await go('/checkout');
await fillAddress();
await clickText('.pay-option', 'Pay by card');
check('choosing card shows the test-mode notice', (await text('.test-banner')).toUpperCase().includes('TEST MODE'));
await page.click('form .submit-btn');
await page.waitForFunction(() => location.pathname.startsWith('/pay/simulate/'), { timeout: 10000 }).catch(() => {});
where = 'pay simulate';
await sleep(600);
check('card checkout redirects to the payment page', new URL(page.url()).pathname.startsWith('/pay/simulate/'), page.url());
await shot('qa-pay-simulate');
const ref = new URL(page.url()).pathname.split('/').pop();
const beforePaid = await call('POST', '/auth/login', null, { identifier: email, password: 'correct-horse-battery' });
const tok = beforePaid.token;
check('the order waits as Awaiting payment meanwhile', (await call('GET', '/orders', tok)).some((o) => o.status === 'AWAITING_PAYMENT'));
await clickText('.pay-actions .cart-btn', 'Simulate successful payment');
await page.waitForFunction(() => location.pathname === '/pay/return', { timeout: 8000 }).catch(() => {});
where = 'pay return (paid)';
await sleep(1200);
check('after paying, the return page confirms it', (await text('.pay-card')).includes('Payment received'), await text('.pay-card'));
await shot('qa-pay-return-ok');
check('the card order is now Placed', (await call('GET', '/orders', tok)).some((o) => o.paymentMethod === 'CARD' && o.status === 'PLACED'));

// ---------- 6. orders list and detail, tracker (admin advances the order) ----------
const admin = (await call('POST', '/auth/admin/login', null, { identifier: 'e2eadmin', password: 'e2e-admin-password' })).token;
const cardOrder = (await call('GET', '/orders', tok)).find((o) => o.paymentMethod === 'CARD');
await call('PATCH', `/admin/orders/${cardOrder.id}/status`, admin, { status: 'PROCESSING' });
await go('/orders', 'orders list');
check('orders list shows both orders', (await page.$$('.order-card')).length === 2, String((await page.$$('.order-card')).length));
await shot('qa-orders');
await go(`/orders/${cardOrder.id}`, 'order detail (card)');
check('detail shows the Processing step as current', (await text('.tracker li.now')).includes('Processing'));
check('detail says paid by card', (await text('.order-detail-grid')).includes('Card'));
await shot('qa-order-card');

// ---------- 7. card payment: abandon ----------
await go(productPath, 'product');
await page.click('.buy-box .cart-btn');
await sleep(600);
await go('/checkout');
await fillAddress();
await clickText('.pay-option', 'Pay by card');
await page.click('form .submit-btn');
await page.waitForFunction(() => location.pathname.startsWith('/pay/simulate/'), { timeout: 10000 }).catch(() => {});
await sleep(400);
await clickText('.pay-actions .side-btn', 'Simulate cancelling');
await page.waitForFunction(() => location.pathname === '/pay/return', { timeout: 8000 }).catch(() => {});
where = 'pay return (cancelled)';
await sleep(1000);
check('cancelling shows "Payment cancelled" and nothing charged', (await text('.pay-card')).includes('Payment cancelled') && (await text('.pay-card')).includes('Nothing was charged'), await text('.pay-card'));
await shot('qa-pay-return-cancelled');

// ---------- 8. cancel the paid card order -> refund ----------
await go(`/orders/${cardOrder.id}`, 'order detail');
check('a Processing order cannot be cancelled by the customer', (await clickText('.side-btn', 'Cancel order')) === false);
await call('PATCH', `/admin/orders/${cardOrder.id}/status`, admin, { status: 'CANCELLED' });
const pay = await call('GET', `/payments/${cardOrder.checkoutRef}`, tok);
check('admin cancelling the paid card order refunded it in full', pay.refundedAmount === pay.amount, JSON.stringify(pay));

// ---------- 9. sign out, and sign back in through the form ----------
await go('/', 'home');
await clickText('.header-actions button', 'Sign out');
await sleep(600);
check('sign out shows the sign-in link again', (await text('.header-actions')).includes('sign in'));
await go('/login');
await page.type('input[aria-label="Email"]', email);
await page.type('input[aria-label="Password"]', 'correct-horse-battery');
await page.click('.login-button');
await page.waitForFunction(() => location.pathname === '/', { timeout: 8000 }).catch(() => {});
check('login form works', new URL(page.url()).pathname === '/' && (await text('.header-actions')).includes('Quinn'));
expectingRejection = true; // the wrong password below is meant to be refused with a 401
await page.evaluate(() => localStorage.clear()); // signed-in visitors are redirected away from /login
await go('/login');
await page.type('input[aria-label="Email"]', email);
await page.type('input[aria-label="Password"]', 'wrong-password-123');
await page.click('.login-button');
await sleep(800);
check('a wrong password shows an error and stays on the login page', new URL(page.url()).pathname === '/login' && (await page.$('.error-text, .notice.error, [role=alert]')) !== null);
expectingRejection = false;

// ---------- 10. sweep of pages for console errors ----------
const sweep = ['/', '/deals', '/products', '/products?minRating=4&sort=rating', '/sell', '/wishlist', '/account/reviews', '/account/unrated', '/nope-not-a-page'];
for (const p of sweep) await go(p);

console.log('\n=== problems seen in the browser ===');
const unique = [...new Set(problems)];
console.log(unique.length ? unique.join('\n') : 'none');
await browser.close();
const failed = results.filter((r) => !r).length;
console.log(`\n${results.length - failed}/${results.length} checks passed, ${unique.length} distinct browser problem(s)`);
process.exit(failed || unique.length ? 1 : 0);
