// Delivery options and dates in a real browser: a seller sets their own free-delivery amount and dispatch time in
// Seller Central; the product page promises dates (standard and express); the cart shows them; at checkout the
// shopper picks express, the total follows, and the order page shows the promised arrival.
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
const admin = (await call('POST', '/auth/admin/login', null, { identifier: 'e2eadmin', password: 'e2e-admin-password' })).token;
async function customer(name) {
  const email = `${name.split(' ')[0].toLowerCase()}${stamp}@example.com`;
  const reg = await call('POST', '/auth/register', null, { name, email, password: 'correct-horse-battery' });
  await confirmEmail(email);
  return reg;
}
const seller = await customer('Dora Dispatch');
const store = await call('POST', '/seller/apply', seller.token, { storeName: `Dispatch Co ${stamp % 10000}`, description: 'Fast.' });
await call('PATCH', `/admin/sellers/${store.id}/status`, admin, { status: 'APPROVED' });
const product = await call('POST', '/seller/products', seller.token, { name: `Delivery Lamp ${stamp % 10000}`, price: '20.00', stock: 9 });
const buyer = await customer('Bea Buyer');

const browser = await puppeteer.launch({ executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: 'new', args: ['--hide-scrollbars'] });
const tab = await browser.newPage();
await tab.setViewport({ width: 1440, height: 900 });
tab.on('pageerror', (e) => problems.push(`[${where}] uncaught: ${e.message}`));
tab.on('console', (m) => { if (m.type() === 'error') problems.push(`[${where}] console.error: ${m.text().slice(0, 160)}`); });
tab.on('response', (r) => { const u = r.url(); if (u.startsWith(BASE) && r.status() >= 400 && !u.includes('/@vite')) problems.push(`[${where}] HTTP ${r.status()} ${r.request().method()} ${u.replace(BASE, '')}`); });
async function go(path, label = path) { where = label; await tab.goto(BASE + path, { waitUntil: 'networkidle0' }); await sleep(400); }
async function as(token) { await tab.goto(BASE + '/login', { waitUntil: 'domcontentloaded' }); await tab.evaluate((t) => localStorage.setItem('pacific.token', t), token); }
async function shot(name) { await tab.screenshot({ path: `${OUT}${name}.png`, fullPage: true }); }
const text = (sel) => tab.$eval(sel, (e) => e.textContent).catch(() => '');
const DAY = /(Mon|Tue|Wed|Thu|Fri|Sat|Sun)[a-z]* \d{1,2} [A-Z][a-z]+/;

// ---------- the seller's delivery settings ----------
await as(seller.token);
await go('/seller/settings', 'seller settings');
await tab.type('#ss-free', '15');
await tab.select('#ss-days', '2');
await tab.evaluate(() => [...document.querySelectorAll('button')].find((b) => b.textContent.includes('Save delivery settings')).click());
await sleep(1000);
const me = await call('GET', '/seller/me', seller.token);
check('a seller can set their own free-delivery amount and dispatch time', Number(me.freeDeliveryThreshold) === 15 && me.dispatchDays === 2, JSON.stringify(me).slice(0, 200));

// ---------- the product page promises dates ----------
await as(buyer.token);
await go(`/products/${product.id}`, 'product page');
const box = await text('.bb-delivery');
check('the buy box promises free standard delivery with dates (over the seller\'s £15)', box.includes('FREE delivery') && DAY.test(box), box);
check('...and the faster express option with its price', box.includes('fastest delivery') && box.includes('£5.99'), box);
await shot('qa-delivery-pdp');

// ---------- cart and checkout ----------
await call('POST', '/cart/items', buyer.token, { productId: product.id, quantity: 1 });
await go('/cart', 'cart');
check('the cart shows when each seller\'s items arrive', DAY.test(await text('.cart-delivery')), await text('.cart-delivery'));

await go('/checkout', 'checkout');
const total = () => text('.totals .line.grand');
const before = await total();
check('checkout starts with free standard delivery', before.includes('£20.00') && (await text('.totals')).includes('FREE'), before);
const expressLabel = await tab.evaluateHandle(() => [...document.querySelectorAll('[role=radiogroup][aria-label^="Delivery for"] label')].find((l) => l.textContent.includes('Express')));
await expressLabel.click();
await sleep(300);
check('choosing express adds its price to the total', (await total()).includes('£25.99'), await total());
await shot('qa-delivery-checkout');
await tab.type('#line1', '1 High Street');
await tab.type('#city', 'Uxbridge');
await tab.type('#postcode', 'UB8 1AA');
await tab.click('form .submit-btn');
await tab.waitForFunction(() => /^\/orders\/\d+$/.test(location.pathname), { timeout: 8000 }).catch(() => {});
await sleep(600);
where = 'order page';
const orderText = await text('.app-main');
check('the order page shows the promised arrival, by express', /Arriving .*(Mon|Tue|Wed|Thu|Fri)/.test(orderText) && orderText.includes('Express'), orderText.slice(0, 400));
const orderId = Number(new URL(tab.url()).pathname.split('/').pop());
const order = await call('GET', `/orders/${orderId}`, buyer.token);
check('...and the order charged express delivery', Number(order.shipping) === 5.99 && order.deliveryOption === 'EXPRESS', JSON.stringify(order).slice(0, 200));

// ---------- phone ----------
await call('POST', '/cart/items', buyer.token, { productId: product.id, quantity: 1 });
await tab.setViewport({ width: 390, height: 844 });
await go('/checkout', 'checkout (phone)');
const overflow = await tab.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
check('checkout with delivery choices fits a phone', overflow <= 0, `overflows by ${overflow}px`);

console.log('\n=== problems seen in the browser ===');
const unique = [...new Set(problems)];
console.log(unique.length ? unique.join('\n') : 'none');
await browser.close();
const failed = results.filter((r) => !r).length;
console.log(`\n${results.length - failed}/${results.length} checks passed, ${unique.length} distinct browser problem(s)`);
process.exit(failed || unique.length ? 1 : 0);
