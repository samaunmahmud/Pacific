// Search and recommendations in a real browser: suggestions as you type (with the keyboard), word-by-word search
// ranked by best match, "Frequently bought together" with one-click add-all, related products, and "Buy it again" on
// the home page and on a delivered order.
import puppeteer from 'puppeteer-core';
import { confirmEmail } from './confirm.mjs';

const BASE = process.env.QA_BASE || 'http://localhost:5180';
const OUT = new URL('./shots/', import.meta.url).pathname;
const API = BASE + '/api';
const results = [];
const problems = [];
let where = 'start';

const check = (name, ok, detail = '') => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + name + (ok ? '' : `  [${String(detail).slice(0, 300)}]`)); };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
async function call(method, url, token, body) {
  const r = await fetch(API + url, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) }, body: body ? JSON.stringify(body) : undefined });
  const t = await r.text();
  if (r.status >= 400) throw new Error(`${method} ${url} -> ${r.status} ${t.slice(0, 200)}`);
  return t ? JSON.parse(t) : null;
}
const stamp = Date.now();
const tag = `zx${stamp % 1000000}`; // a made-up word so searches only find this run's products
const admin = (await call('POST', '/auth/admin/login', null, { identifier: 'e2eadmin', password: 'e2e-admin-password' })).token;
async function customer(name) {
  const email = `${name.split(' ')[0].toLowerCase()}${stamp}@example.com`;
  const reg = await call('POST', '/auth/register', null, { name, email, password: 'correct-horse-battery' });
  await confirmEmail(email);
  return reg;
}
const seller = await customer('Finn Finder');
const store = await call('POST', '/seller/apply', seller.token, { storeName: `Finder Goods ${stamp % 10000}`, description: 'x' });
await call('PATCH', `/admin/sellers/${store.id}/status`, admin, { status: 'APPROVED' });
const cat = await call('POST', '/admin/categories', admin, { name: `Tea ${tag}` });
const make = (name, price) => call('POST', '/seller/products', seller.token, { name, price, stock: 20, categoryId: cat.id, description: 'Loose-leaf friendly' });
const kettle = await make(`Walnut Kettle ${tag}`, '30.00');
const teapot = await make(`Glass Teapot ${tag}`, '18.00');
const caddy = await make(`Tea Caddy ${tag}`, '9.00');

// Two earlier shoppers bought the kettle and teapot together; one order is delivered.
const buyer = await customer('Bea Buyer');
for (const who of [await customer('Early Bird'), buyer]) {
  await call('POST', '/cart/items', who.token, { productId: kettle.id, quantity: 1 });
  await call('POST', '/cart/items', who.token, { productId: teapot.id, quantity: 1 });
  const placed = await call('POST', '/orders', who.token, { name: 'X', line1: '1 High St', city: 'Uxbridge', postcode: 'UB8 1AA', country: 'UK' });
  if (who === buyer) for (const s of ['PROCESSING', 'SHIPPED', 'DELIVERED']) await call('PATCH', `/seller/orders/${placed.orders[0].id}/status`, seller.token, { status: s });
}
const deliveredOrder = (await call('GET', '/orders', buyer.token))[0];

const browser = await puppeteer.launch({ executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: 'new', args: ['--hide-scrollbars'] });
const tab = await browser.newPage();
await tab.setViewport({ width: 1440, height: 900 });
tab.on('pageerror', (e) => problems.push(`[${where}] uncaught: ${e.message}`));
tab.on('console', (m) => { if (m.type() === 'error') problems.push(`[${where}] console.error: ${m.text().slice(0, 160)}`); });
tab.on('response', (r) => { const u = r.url(); if (u.startsWith(BASE) && r.status() >= 400 && !u.includes('/@vite')) problems.push(`[${where}] HTTP ${r.status()} ${r.request().method()} ${u.replace(BASE, '')}`); });
async function go(path, label = path) { where = label; await tab.goto(BASE + path, { waitUntil: 'networkidle0' }); await sleep(400); }
async function shot(name) { await tab.screenshot({ path: `${OUT}${name}.png`, fullPage: true }); }
const text = (sel) => tab.$eval(sel, (e) => e.textContent).catch(() => '');
const cartCount = async () => (await call('GET', '/cart', buyer.token)).items.length;

await tab.goto(BASE + '/login', { waitUntil: 'domcontentloaded' });
await tab.evaluate((t) => localStorage.setItem('pacific.token', t), buyer.token);

// ---------- suggestions ----------
await go('/', 'home');
check('the home page offers "Buy it again" from delivered orders', (await text('section[aria-label="Buy it again"]')).includes(`Walnut Kettle ${tag}`), await text('section[aria-label="Buy it again"]'));
await tab.type('.transparent-search', `kettle ${tag}`);
await tab.waitForSelector('.suggest-list', { timeout: 5000 }).catch(() => {});
check('typing shows suggestions', (await text('.suggest-list')).includes(`Walnut Kettle ${tag}`), await text('.suggest-list'));
await shot('qa-discovery-suggest');
await tab.keyboard.press('ArrowDown');
await tab.keyboard.press('Enter');
await tab.waitForFunction(() => /^\/products\/\d+$/.test(location.pathname), { timeout: 5000 }).catch(() => {});
check('arrow keys and Enter open the suggested product', new URL(tab.url()).pathname === `/products/${kettle.id}`, tab.url());

// ---------- search ----------
await go(`/products?q=${encodeURIComponent(`${tag} teapot glass`)}`, 'search');
const found = await tab.$$eval('.product-card .card-title', (els) => els.map((e) => e.textContent));
check('words can come in any order', found.length === 1 && found[0] === `Glass Teapot ${tag}`, JSON.stringify(found));
check('a search is sorted by best match', (await tab.$eval('.sort-by select', (s) => s.value).catch(() => '')) === 'relevance');

// ---------- product page recommendations ----------
await go(`/products/${kettle.id}`, 'kettle page');
const fbt = await text('.fbt');
check('"Frequently bought together" shows the teapot with the total', fbt.includes(`Glass Teapot ${tag}`) && fbt.includes('£48.00'), fbt);
check('related products come from the same category', (await text('section[aria-label="Related products"]')).includes(`Tea Caddy ${tag}`), await text('section[aria-label="Related products"]'));
await shot('qa-discovery-pdp');
const before = await cartCount();
await tab.click('.fbt .cart-btn');
await sleep(1200);
check('"Add all" puts both in the cart', (await cartCount()) === before + 2, `${before} -> ${await cartCount()}`);

// ---------- buy it again from an order ----------
await call('DELETE', `/cart/items/${kettle.id}`, buyer.token);
await go(`/orders/${deliveredOrder.id}`, 'delivered order');
await tab.evaluate(() => [...document.querySelectorAll('.buy-again')][0].click());
await sleep(1000);
check('"Buy it again" on a delivered order adds it back', (await call('GET', '/cart', buyer.token)).items.some((i) => i.productId === kettle.id));

// ---------- phone ----------
await tab.setViewport({ width: 390, height: 844 });
await go(`/products/${kettle.id}`, 'kettle page (phone)');
const overflow = await tab.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
check('recommendations fit a phone', overflow <= 0, `overflows by ${overflow}px`);

console.log('\n=== problems seen in the browser ===');
const unique = [...new Set(problems)];
console.log(unique.length ? unique.join('\n') : 'none');
await browser.close();
const failed = results.filter((r) => !r).length;
console.log(`\n${results.length - failed}/${results.length} checks passed, ${unique.length} distinct browser problem(s)`);
process.exit(failed || unique.length ? 1 : 0);
