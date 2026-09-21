// Real-browser check: abandon or cancel a card payment and the cart comes back. Runs against BASE (dev server).
import puppeteer from 'puppeteer-core';

const BASE = process.env.BASE || 'http://localhost:5180';
const API = BASE + '/api';
const OUT = new URL('./shots/', import.meta.url).pathname;
const problems = [];
let where = 'start';
let fails = 0;
const check = (name, ok, detail = '') => { if (!ok) fails++; console.log((ok ? 'PASS ' : 'FAIL ') + name + (ok ? '' : `  [${detail}]`)); };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
async function call(method, url, token, body) {
  const r = await fetch(API + url, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) }, body: body ? JSON.stringify(body) : undefined });
  const t = await r.text();
  return { status: r.status, body: t ? JSON.parse(t) : null };
}

// a customer with two products in the cart, set up over the API
const email = `abandon${Date.now()}@example.com`;
const reg = await call('POST', '/auth/register', null, { name: 'Abby Doner', email, password: 'correct-horse-battery' });
const token = reg.body.token;
const list = await call('GET', '/products?size=40', token);
const stocked = list.body.items.filter((p) => p.stock >= 6 && p.imageUrl);
const [a, b] = stocked;
await call('POST', '/cart/items', token, { productId: a.id, quantity: 2 });
await call('POST', '/cart/items', token, { productId: b.id, quantity: 1 });
const cartCount = async () => (await call('GET', '/cart', token)).body.itemCount;
check('setup: 3 units in the cart', (await cartCount()) === 3);

const browser = await puppeteer.launch({ executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: 'new', args: ['--hide-scrollbars'] });
const page = await browser.newPage();
await page.setViewport({ width: 1280, height: 900 });
await page.evaluateOnNewDocument((t) => localStorage.setItem('pacific.token', t), token);
page.on('dialog', (d) => d.accept());
page.on('pageerror', (e) => problems.push(`[${where}] uncaught: ${e.message}`));
page.on('console', (m) => { if (m.type() === 'error') problems.push(`[${where}] console.error: ${m.text().slice(0, 160)}`); });
page.on('response', (r) => { const u = r.url(); if (u.startsWith(BASE) && r.status() >= 400 && !u.includes('/@vite') && !u.endsWith('favicon.ico')) problems.push(`[${where}] HTTP ${r.status()} ${r.request().method()} ${u.replace(BASE, '')}`); });
const go = async (path, label = path) => { where = label; await page.goto(BASE + path, { waitUntil: 'networkidle0' }); await sleep(300); };
const shot = (n) => page.screenshot({ path: `${OUT}${n}.png`, fullPage: true });
const body = () => page.$eval('body', (e) => e.innerText);

async function cardCheckoutViaUi() {
  await go('/checkout');
  // a customer who has ordered before is offered their saved address; ask for a new one so the form shows
  await page.waitForFunction(() => document.querySelector('#line1') || document.querySelector('input[name="address"]'), { timeout: 8000 });
  if (!(await page.$('#line1'))) {
    await page.evaluate(() => [...document.querySelectorAll('label.pay-option')].find((l) => l.textContent.includes('Use a different address')).querySelector('input').click());
  }
  await page.waitForSelector('#line1', { timeout: 8000 });
  await page.type('#line1', '1 Test Street');
  await page.type('#city', 'London');
  await page.type('#postcode', 'N1 1AA');
  await page.click('[aria-label="Payment method"] .pay-option:nth-child(2)'); // pay by card now
  await page.click('.submit-btn');
  await page.waitForFunction(() => location.pathname.startsWith('/pay/simulate/'), { timeout: 10000 });
  await sleep(400);
}

// ---- 1. customer walks away from the payment page, then cancels from the return page ----
await cardCheckoutViaUi();
check('card checkout reaches the (simulated) payment page', page.url().includes('/pay/simulate/'));
check('the cart was emptied while the payment is open', (await cartCount()) === 0);

await page.click('a.link-plain'); // "Leave without paying"
await page.waitForFunction(() => location.pathname === '/pay/return', { timeout: 8000 });
await sleep(600);
check('return page says the payment is not completed', (await body()).includes('Payment not completed yet'));
await shot('abandon-1-not-completed');

const cancel = await page.$$('button.link-plain');
await cancel[0].click();
await page.waitForFunction(() => document.body.innerText.includes('Payment cancelled'), { timeout: 8000 });
await sleep(600);
const txt = await body();
check('cancelled page tells them the items are back in the cart', txt.includes('back in your cart'), txt.slice(0, 200));
check('cart has all 3 units again', (await cartCount()) === 3);
await shot('abandon-2-cancelled');

await page.click('a.cart-btn'); // "Back to cart"
await page.waitForFunction(() => location.pathname === '/cart', { timeout: 8000 });
await sleep(600);
const cartText = await body();
check('cart page shows both products again', cartText.includes(a.name) && cartText.includes(b.name), cartText.slice(0, 300));
check('the quantities are exactly what they had (2 and 1)', (await page.$$eval('.cart-item', (els) => els.length)) === 2);
await shot('abandon-3-cart-restored');

// ---- 2. the normal paid path still leaves the cart empty ----
await cardCheckoutViaUi();
await page.evaluate(() => [...document.querySelectorAll('button')].find((b) => b.textContent.includes('Simulate successful')).click());
await page.waitForFunction(() => document.body.innerText.includes('Payment received'), { timeout: 8000 });
check('a paid card checkout completes', true);
check('and the cart stays empty after paying', (await cartCount()) === 0);

await browser.close();
console.log(problems.length ? '\nPROBLEMS:\n' + problems.join('\n') : '\nno console errors or failed requests');
console.log(`\n${fails} failure(s)`);
process.exit(fails || problems.length ? 1 : 0);
