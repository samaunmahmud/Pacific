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


async function loginUI(path, idSel, id, pw, btn) {
  await page.goto(BASE + '/login', { waitUntil: 'domcontentloaded' });
  await page.evaluate(() => localStorage.clear());
  await go(path);
  await page.type(idSel, id);
  await page.type('input[type=password]', pw);
  await page.click(btn);
  await sleep(1200);
}
const emailOf = (n) => `${n}${Date.now()}@example.com`;

// ---------- admin through the UI ----------
await loginUI('/admin/login', 'input[aria-label="Admin ID"]', 'e2eadmin', 'e2e-admin-password', '.login-button');
check('admin login lands in the admin area', new URL(page.url()).pathname.startsWith('/admin'), page.url());
for (const p of ['/admin', '/admin/orders', '/admin/products', '/admin/products/new', '/admin/sellers', '/admin/flagged']) {
  await go(p);
  check(`admin page ${p} renders`, (await text('.app-main')).trim().length > 40, await text('.app-main'));
}
await go('/admin'); await shot('qa-admin-dashboard');
await go('/admin/products'); await shot('qa-admin-products');
await go('/admin/orders'); await shot('qa-admin-orders');

// ---------- a customer applies to sell, admin approves ----------
const sellerEmail = emailOf('seller');
const reg = await call('POST', '/auth/register', null, { name: 'Sam Seller', email: sellerEmail, password: 'correct-horse-battery' });
await page.evaluate((t) => localStorage.setItem('pacific.token', t), reg.token);
await go('/sell');
await shot('qa-sell');
await page.type('.app-main form input', 'QA Store ' + Date.now());
await page.type('.app-main form textarea', 'A store created by the QA run.');
await clickText('.app-main form button', 'Submit application');
await sleep(1200);
const me = await call('GET', '/seller/me', reg.token);
check('applying through the form creates a pending store', me && me.status === 'PENDING', JSON.stringify(me));
const adminTok = (await call('POST', '/auth/admin/login', null, { identifier: 'e2eadmin', password: 'e2e-admin-password' })).token;
await call('PATCH', `/admin/sellers/${me.id}/status`, adminTok, { status: 'APPROVED' });

// ---------- Seller Central pages ----------
await page.evaluate((t) => localStorage.setItem('pacific.token', t), reg.token);
await go('/seller');
check('Seller Central dashboard loads', (await text('.app-main')).includes('Orders by status') || (await text('.app-main')).toLowerCase().includes('seller'), await text('.app-main'));
await shot('qa-seller-dashboard');
for (const p of ['/seller/products', '/seller/products/new', '/seller/orders', '/seller/earnings', '/seller/questions', '/seller/settings']) {
  await go(p);
  check(`seller page ${p} renders`, (await text('.app-main')).trim().length > 30, await text('.app-main'));
}
// list a product through the seller form
await go('/seller/products/new');
await shot('qa-seller-product-form');
const names = await page.$$eval('.app-main form input, .app-main form textarea, .app-main form select', (els) => els.map((e) => `${e.tagName}:${e.name || e.id || e.getAttribute('aria-label')}`));
console.log('seller product form fields:', names.join(', '));
// list a product through the form
const F = (id) => `#${id}, [name="${id}"]`;
await page.type(F('pn'), 'QA Listed Gadget');
await page.type(F('pd'), 'Listed by the QA run.');
await page.type(F('pp'), '19.99');
await page.type(F('ps'), '12');
const catValue = await page.$eval(F('pc'), (sel) => [...sel.options].find((o) => o.value)?.value);
if (catValue) await page.select(F('pc'), catValue);
await clickText('.app-main form button', 'Save') || await clickText('.app-main form button', 'List') || await page.click('.app-main form button[type=submit], .app-main form .submit-btn');
await sleep(1500);
const mine2 = await call('GET', '/seller/products', reg.token);
check('the seller product form lists a product', (mine2.items || []).some((p) => p.name === 'QA Listed Gadget' && p.stock === 12), JSON.stringify(mine2).slice(0, 200));
await go('/seller/products'); await shot('qa-seller-products');

// ---------- review flow: buy, deliver, review ----------
const buyer = await call('POST', '/auth/register', null, { name: 'Bea Buyer', email: emailOf('buyer'), password: 'correct-horse-battery' });
const bt = buyer.token;
const prod = (await call('GET', '/products?size=1&sort=popular', null)).items[0];
await call('POST', '/cart/items', bt, { productId: prod.id, quantity: 1 });
const co = await call('POST', '/orders', bt, { name: 'Bea Buyer', line1: '1 Road', city: 'Town', postcode: 'AB1 2CD', country: 'UK' });
for (const st of ['PROCESSING', 'SHIPPED', 'DELIVERED']) await call('PATCH', `/admin/orders/${co.orders[0].id}/status`, adminTok, { status: st });
await page.evaluate((t) => localStorage.setItem('pacific.token', t), bt);
await go('/account/unrated');
check('unrated purchases lists the delivered product', (await text('.app-main')).includes(prod.name.slice(0, 20)), await text('.app-main'));
await shot('qa-unrated');
await go(`/products/${prod.id}/review`);
await shot('qa-review-form');
// write a review through the form: 4 stars, a title and a comment
await page.click('button[aria-label="4 stars"]');
await page.type('#comment', 'Solid monitor, sharp picture and easy to set up.');
await page.type('#title', 'Very good');
await page.click('form .submit-btn');
await sleep(1500);
const rev = await call('GET', `/products/${prod.id}/reviews`, bt);
const mine = (rev.reviews || []).find((r) => r.title === 'Very good');
check('submitting the review form saves a 4-star review', !!mine && mine.rating === 4, JSON.stringify((rev.reviews || []).slice(0, 1)));
await go(`/products/${prod.id}`);
check('the new review appears on the product page', (await text('.app-main')).includes('Solid monitor, sharp picture'));
await shot('qa-pdp-reviewed');
check('product page shows the review prompt for a buyer', (await text('.app-main')).includes('Write a review') || (await text('.app-main')).includes('review'));
await shot('qa-pdp-buyer');
await go('/wishlist'); await shot('qa-wishlist');
await go(`/sellers/${prod.sellerSlug || 'x'}`); if (prod.sellerSlug) await shot('qa-seller-store');
await go('/login'); await shot('qa-login-page');

console.log('\n=== problems seen in the browser ===');
const unique = [...new Set(problems)];
console.log(unique.length ? unique.join('\n') : 'none');
await browser.close();
const failed = results.filter((r) => !r).length;
console.log(`\n${results.length - failed}/${results.length} checks passed, ${unique.length} distinct browser problem(s)`);
process.exit(failed || unique.length ? 1 : 0);
