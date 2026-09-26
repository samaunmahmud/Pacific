// Promotions in a real browser: a seller starts a Lightning Deal, a coupon and a promo code from Seller Central; a
// shopper sees the deal on the card and product page, clips the coupon, gets both prices in the cart, applies the
// code at checkout (and a bad one is refused), and the order and Seller Central show what was used.
import puppeteer from 'puppeteer-core';
import { confirmEmail } from './confirm.mjs';

const BASE = process.env.QA_BASE || 'http://localhost:5180';
const OUT = new URL('./shots/', import.meta.url).pathname;
const API = BASE + '/api';
const results = [];
const problems = [];
let where = 'start';
let expecting = [];

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
const seller = await customer('Petra Promo');
const store = await call('POST', '/seller/apply', seller.token, { storeName: `Promo Palace ${stamp % 10000}`, description: 'Bargains.' });
await call('PATCH', `/admin/sellers/${store.id}/status`, admin, { status: 'APPROVED' });
const lampName = `Deal Lamp ${stamp % 10000}`;
const rugName = `Coupon Rug ${stamp % 10000}`;
const lamp = await call('POST', '/seller/products', seller.token, { name: lampName, price: '40.00', stock: 10 });
const rug = await call('POST', '/seller/products', seller.token, { name: rugName, price: '50.00', stock: 10 });
const code = `QA${stamp % 100000}`;
const buyer = await customer('Bea Buyer');

const browser = await puppeteer.launch({ executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: 'new', args: ['--hide-scrollbars'] });
const tab = await browser.newPage();
await tab.setViewport({ width: 1440, height: 900 });
tab.on('pageerror', (e) => problems.push(`[${where}] uncaught: ${e.message}`));
tab.on('console', (m) => { if (m.type() === 'error' && !expecting.some((s) => m.text().includes(String(s)))) problems.push(`[${where}] console.error: ${m.text().slice(0, 160)}`); });
tab.on('response', (r) => { const u = r.url(); if (u.startsWith(BASE) && r.status() >= 400 && !expecting.includes(r.status()) && !u.includes('/@vite')) problems.push(`[${where}] HTTP ${r.status()} ${r.request().method()} ${u.replace(BASE, '')}`); });
async function go(path, label = path) { where = label; await tab.goto(BASE + path, { waitUntil: 'networkidle0' }); await sleep(400); }
async function as(token) { await tab.goto(BASE + '/login', { waitUntil: 'domcontentloaded' }); await tab.evaluate((t) => localStorage.setItem('pacific.token', t), token); }
async function shot(name) { await tab.screenshot({ path: `${OUT}${name}.png`, fullPage: true }); }
const text = (sel) => tab.$eval(sel, (e) => e.textContent).catch(() => '');
async function setField(sel, value) { await tab.$eval(sel, (e) => { e.value = ''; }); await tab.type(sel, value); }
async function submitForm(label) { await tab.evaluate((l) => document.querySelector(`form[aria-label="${l}"] .submit-btn`).click(), label); await sleep(1000); }

// ---------- the seller sets up promotions ----------
await as(seller.token);
await go('/seller/promotions', 'seller promotions');
await tab.select('#dl-p', String(lamp.id));
await setField('#dl-price', '30');
await setField('#dl-q', '2');
await setField('#dl-h', '3');
await submitForm('New Lightning Deal');
check('a seller starts a Lightning Deal', (await text('.promo-section')).includes(lampName) && (await text('.promo-section')).includes('0 / 2'), await text('.promo-section'));
await tab.select('#cp-p', String(rug.id));
await setField('#cp-pc', '10');
await submitForm('New coupon');
await setField('#pc-c', code);
await setField('#pc-pc', '15');
await submitForm('New promo code');
const setup = await call('GET', '/seller/promotions', seller.token);
check('...a coupon and a promo code', setup.coupons.length === 1 && setup.codes.length === 1 && setup.codes[0].code === code, JSON.stringify(setup).slice(0, 200));
await shot('qa-promotions-seller');

// ---------- the shopper sees them ----------
await as(buyer.token);
await go(`/products?q=${encodeURIComponent(lampName)}`, 'search');
const card = await text('.product-card');
check('the product card shows the Lightning Deal', card.includes('Lightning Deal') && card.includes('-25%') && card.includes('£3000'), card);
await go(`/products/${lamp.id}`, 'deal product page');
check('the buy box shows the deal price, countdown and claimed bar', (await text('.buy-box')).includes('Lightning Deal') && (await text('.buy-box')).includes('Ends in') && (await tab.$('.buy-box .deal-bar')) !== null, await text('.buy-box'));
await shot('qa-promotions-deal');
await tab.click('.buy-box .cart-btn');
await sleep(800);

await go(`/products/${rug.id}`, 'coupon product page');
check('the buy box offers the coupon', (await text('.bb-coupon')).includes('Apply 10% coupon'), await text('.buy-box'));
await tab.click('.bb-coupon input');
await sleep(1000);
check('clipping it marks it applied', (await text('.bb-coupon')).includes('coupon applied'), await text('.bb-coupon'));
await tab.click('.buy-box .cart-btn');
await sleep(800);

// ---------- cart and checkout ----------
await go('/cart', 'cart');
const cartText = await text('.cart-main');
check('the cart shows the deal and the coupon with the regular prices', cartText.includes('Lightning Deal') && cartText.includes('Coupon 10%') && cartText.includes('£40.00') && cartText.includes('£45.00'), cartText);

await go('/checkout', 'checkout');
expecting = [];
await tab.type('input[aria-label="Promo code"]', 'NOTACODE');
await tab.evaluate(() => [...document.querySelectorAll('.promo-box button')].find((b) => b.textContent.includes('Apply')).click());
await sleep(800);
check('an unknown code is refused with a reason', (await text('.promo-box')).includes("isn't a valid promo code"), await text('.promo-box'));
await setField('input[aria-label="Promo code"]', code);
await tab.evaluate(() => [...document.querySelectorAll('.promo-box button')].find((b) => b.textContent.includes('Apply')).click());
await sleep(800);
const totals = await text('.totals');
// 30 (deal) + 45 (coupon) = 75, then 15% off the store's items = 63.75
check('the code takes 15% off the store\'s items', totals.includes(`Code ${code}`) && totals.includes('£63.75'), totals);
await shot('qa-promotions-checkout');
await tab.type('#line1', '1 High Street');
await tab.type('#city', 'Uxbridge');
await tab.type('#postcode', 'UB8 1AA');
await tab.click('form .submit-btn');
await tab.waitForFunction(() => /^\/orders\/\d+$/.test(location.pathname), { timeout: 8000 }).catch(() => {});
await sleep(600);
where = 'order page';
const orderText = await text('.app-main');
check('the order shows each line\'s promotion', orderText.includes('Lightning Deal · Code') && orderText.includes('Coupon 10% · Code'), orderText.slice(0, 500));

// ---------- Seller Central shows what was used ----------
const after = await call('GET', '/seller/promotions', seller.token);
check('Seller Central counts the deal units, coupon and code used', after.deals[0].claimed === 1 && after.coupons[0].used === 1 && after.codes[0].used === 1, JSON.stringify(after).slice(0, 300));

// ---------- phone ----------
await as(seller.token);
await tab.setViewport({ width: 390, height: 844 });
await go('/seller/promotions', 'promotions (phone)');
const overflow = await tab.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
check('the promotions page fits a phone', overflow <= 0, `overflows by ${overflow}px`);

console.log('\n=== problems seen in the browser ===');
const unique = [...new Set(problems)];
console.log(unique.length ? unique.join('\n') : 'none');
await browser.close();
const failed = results.filter((r) => !r).length;
console.log(`\n${results.length - failed}/${results.length} checks passed, ${unique.length} distinct browser problem(s)`);
process.exit(failed || unique.length ? 1 : 0);
