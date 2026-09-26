// Several sellers per product in a real browser: a second store joins a product page with "Sell on Pacific", its
// cheaper offer wins the buy box, search shows one card that buys from the buy box, shoppers can pick another seller,
// the buy box moves when the winner sells out, and a seller edits their offer's terms (not the product's details).
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
async function store(name) {
  const owner = await customer(name);
  const s = await call('POST', '/seller/apply', owner.token, { storeName: `${name.split(' ')[0]} Store ${stamp % 10000}`, description: 'x' });
  await call('PATCH', `/admin/sellers/${s.id}/status`, admin, { status: 'APPROVED' });
  return { ...owner, store: s };
}

const productName = `Offer Teapot ${stamp % 100000}`;
const alice = await store('Alice Seller');
const bob = await store('Bob Seller');
const page = await call('POST', '/seller/products', alice.token, { name: productName, price: '30.00', stock: 5, description: 'Pours well.' });
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
const cartLines = async (token) => (await call('GET', '/cart', token)).items.map((i) => ({ id: i.productId, seller: i.sellerName, qty: i.quantity }));

// ---------- a second store joins the product page ----------
await as(bob.token);
await go(`/products/${page.id}`, 'product page (bob)');
check('another approved seller is offered "Sell on Pacific"', (await text('.sell-this')).includes('Have one to sell?'), await text('.buy-box'));
await tab.click('.sell-this button');
await tab.type('.sell-this input[inputmode="decimal"]', '27.50');
await tab.$eval('.sell-this input[type="number"]', (e) => { e.value = ''; });
await tab.type('.sell-this input[type="number"]', '1');
await tab.click('.sell-this .submit-btn');
await sleep(1200);
check('the cheaper new offer wins the buy box', (await text('.buy-box')).includes('£2750') && (await text('.buy-box')).includes(bob.store.storeName), await text('.buy-box'));
check('the first seller is listed under "Other sellers"', (await text('.other-sellers')).includes(alice.store.storeName) && (await text('.other-sellers')).includes('30.00'), await text('.other-sellers'));
check('the seller isn\'t offered to sell it twice', (await tab.$('.sell-this')) === null);

// ---------- one card in search, buying from the buy box ----------
await as(buyer.token);
await go(`/products?q=${encodeURIComponent(productName)}`, 'search');
const cards = await tab.$$('.product-card');
check('search shows one card for the product', cards.length === 1, cards.length);
const card = await text('.product-card');
check('the card shows the buy-box price, seller and the other offers', card.includes('£2750') && card.includes(bob.store.storeName) && card.includes('+1 other seller'), card);
await tab.click('.product-card .cart-btn');
await sleep(800);
let lines = await cartLines(buyer.token);
check('"Add to cart" on the card buys the buy-box offer', lines.length === 1 && lines[0].seller === bob.store.storeName, JSON.stringify(lines));

// ---------- choosing another seller ----------
await go(`/products/${page.id}`, 'product page (buyer)');
await shot('qa-offers-pdp');
await tab.click('.other-sellers .cart-btn');
await sleep(800);
lines = await cartLines(buyer.token);
check('an offer from "Other sellers" goes in the cart as its own line', lines.length === 2 && lines.some((l) => l.seller === alice.store.storeName), JSON.stringify(lines));

// ---------- the winner sells out ----------
await call('POST', '/orders', buyer.token, { name: 'Bea Buyer', line1: '1 High St', city: 'Uxbridge', postcode: 'UB8 1AA', country: 'UK' });
await go(`/products/${page.id}`, 'product page (after checkout)');
check('when the winner sells out, the buy box moves to the next seller', (await text('.buy-box')).includes('£3000') && (await text('.buy-box')).includes(alice.store.storeName), await text('.buy-box'));
check('...and the sold-out offer says so', (await text('.other-sellers')).includes('Out of stock'), await text('.other-sellers'));

// ---------- the seller edits their offer ----------
await as(bob.token);
const mine = (await call('GET', '/seller/products', bob.token)).items.find((p) => p.catalogId === page.id);
await go(`/seller/products/${mine.id}`, 'edit offer');
check('editing an offer explains it lives on a shared page', (await text('.page')).includes('shared product page'), await text('.page .notice'));
check('...without the name or photo fields', (await tab.$('#pn')) === null && (await tab.$('.photo-field')) === null);
await tab.select('#pcond', 'USED_LIKE_NEW');
await tab.$eval('#ps', (e) => { e.value = ''; });
await tab.type('#ps', '4');
await tab.evaluate(() => [...document.querySelectorAll('.app-main button')].find((b) => b.textContent.includes('Save product')).click());
await sleep(1000);
await go(`/products/${page.id}`, 'product page (used offer)');
check('a used offer shows its condition and doesn\'t take the buy box from a new one', (await text('.other-sellers')).includes('Used – like new') && (await text('.buy-box')).includes(alice.store.storeName), await text('.other-sellers'));

// ---------- phone ----------
await tab.setViewport({ width: 390, height: 844 });
await go(`/products/${page.id}`, 'product page (phone)');
const overflow = await tab.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
check('the product page with other sellers fits a phone', overflow <= 0, `overflows by ${overflow}px`);
await shot('qa-offers-phone');

console.log('\n=== problems seen in the browser ===');
const unique = [...new Set(problems)];
console.log(unique.length ? unique.join('\n') : 'none');
await browser.close();
const failed = results.filter((r) => !r).length;
console.log(`\n${results.length - failed}/${results.length} checks passed, ${unique.length} distinct browser problem(s)`);
process.exit(failed || unique.length ? 1 : 0);
