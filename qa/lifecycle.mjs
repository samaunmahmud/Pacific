// Real-browser check of the order lifecycle: buyer checks out, seller ships with tracking, buyer follows it, admin reads the emails.
import puppeteer from 'puppeteer-core';
import { confirmEmail } from './confirm.mjs';

const BASE = process.env.BASE || 'http://localhost:5180';
const API = BASE + '/api';
const OUT = new URL('./shots/', import.meta.url).pathname;
const problems = [];
let where = 'seed';
let fails = 0;
const check = (name, ok, detail = '') => { if (!ok) fails++; console.log((ok ? 'PASS ' : 'FAIL ') + name + (ok ? '' : `  [${String(detail).slice(0, 300)}]`)); };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
async function call(method, url, token, body) {
  const r = await fetch(API + url, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) }, body: body ? JSON.stringify(body) : undefined });
  const t = await r.text();
  if (r.status >= 400) throw new Error(`${method} ${url} -> ${r.status} ${t.slice(0, 200)}`);
  return t ? JSON.parse(t) : null;
}
const stamp = Date.now();
const reg = async (name) => {
  const email = `${name.split(' ')[0].toLowerCase()}${stamp}@example.com`;
  const { token } = await call('POST', '/auth/register', null, { name, email, password: 'correct-horse-battery' });
  await confirmEmail(email);
  return token;
};

const admin = (await call('POST', '/auth/admin/login', null, { identifier: 'e2eadmin', password: 'e2e-admin-password' })).token;
const seller = await reg('Sasha Seller');
const store = await call('POST', '/seller/apply', seller, { storeName: `Lifecycle Goods ${stamp % 1000}`, description: 'Things for the home.' });
await call('PATCH', `/admin/sellers/${store.id}/status`, admin, { status: 'APPROVED' });
const product = await call('POST', '/seller/products', seller, { name: 'Walnut Serving Board', price: '45.00', stock: 20, description: 'Solid walnut.' });
const buyer = await reg('Bea Buyer');
await call('POST', '/cart/items', buyer, { productId: product.id, quantity: 1 });

const browser = await puppeteer.launch({ executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: 'new', args: ['--hide-scrollbars'] });
async function newPage(token) {
  const page = await browser.newPage();
  await page.setViewport({ width: 1280, height: 900 });
  await page.evaluateOnNewDocument((t) => localStorage.setItem('pacific.token', t), token);
  page.on('dialog', (d) => d.accept());
  page.on('pageerror', (e) => problems.push(`[${where}] uncaught: ${e.message}`));
  page.on('console', (m) => { if (m.type() === 'error') problems.push(`[${where}] console.error: ${m.text().slice(0, 160)}`); });
  page.on('response', (r) => { const u = r.url(); if (u.startsWith(BASE) && r.status() >= 400 && !u.includes('/@vite') && !u.endsWith('favicon.ico')) problems.push(`[${where}] HTTP ${r.status()} ${r.request().method()} ${u.replace(BASE, '')}`); });
  return page;
}
const go = async (page, path, label = path) => { where = label; await page.goto(BASE + path, { waitUntil: 'networkidle0' }); await sleep(300); };
const body = (page) => page.$eval('body', (e) => e.innerText);
const shot = (page, n) => page.screenshot({ path: `${OUT}${n}.png`, fullPage: true });

// ---- 1. buyer checks out (pay on delivery) through the form ----
const b = await newPage(buyer);
await go(b, '/checkout');
await b.type('#line1', '1 Test Street');
await b.type('#city', 'London');
await b.type('#postcode', 'N1 1AA');
await b.click('.submit-btn');
await b.waitForFunction(() => /^\/orders\/\d+$/.test(location.pathname), { timeout: 10000 });
await sleep(500);
const orderId = Number(new URL(b.url()).pathname.split('/').pop());
let txt = await body(b);
check('checkout lands on the order page', orderId > 0, b.url());
check('the order page has an activity timeline with "Order placed"', txt.includes('Order activity') && txt.includes('Order placed') && txt.includes('Pay on delivery'), txt.slice(0, 400));

// ---- 2. seller marks it processing, then ships it with tracking, in the UI ----
const s = await newPage(seller);
await go(s, '/seller/orders');
await s.click('details summary');
await sleep(300);
const sel = `select[aria-label="New status for order ${orderId}"]`;
await s.select(sel, 'PROCESSING');
await s.evaluate(() => [...document.querySelectorAll('button')].find((x) => x.textContent.trim() === 'Apply').click());
await sleep(800);
await s.select(sel, 'SHIPPED');
await sleep(300);
check('choosing "Shipped" reveals carrier and tracking fields', (await s.$(`#carrier-${orderId}`)) !== null && (await s.$(`#tracking-${orderId}`)) !== null);
await shot(s, 'lifecycle-1-ship-form');
await s.type(`#carrier-${orderId}`, 'Royal Mail');
await s.type(`#tracking-${orderId}`, 'AB123456789GB');
await s.evaluate(() => [...document.querySelectorAll('button')].find((x) => x.textContent.trim() === 'Apply').click());
await sleep(1000);
txt = await body(s);
check('seller sees the tracking on the order after shipping', txt.includes('AB123456789GB') && txt.includes('Royal Mail'), txt.slice(0, 300));
check('seller sees "Track parcel" link to Royal Mail', await s.$eval('.tracking-box a', (a) => a.href.startsWith('https://www.royalmail.com/') && a.rel.includes('noopener')).catch(() => false));

// ---- 3. buyer follows the order ----
await go(b, `/orders/${orderId}`);
txt = await body(b);
check('buyer sees "On its way" and the tracking number', txt.includes('On its way') && txt.includes('AB123456789GB'), txt.slice(0, 300));
check('the timeline shows placed, prepared and shipped', ['Order placed', 'Being prepared', 'Shipped'].every((x) => txt.includes(x)), txt.slice(0, 500));
await shot(b, 'lifecycle-2-buyer-order');
await go(b, '/orders');
check('the order list shows the tracking number for a shipped order', (await body(b)).includes('AB123456789GB'));

// ---- 4. admin reads the emails ----
const a = await newPage(admin);
await go(a, '/admin/emails');
txt = await body(a);
check('admin emails page lists the confirmation, new-order and shipped emails',
  txt.includes(`Your Pacific order #${orderId} is confirmed`) && txt.includes(`New Pacific order #${orderId}`) && txt.includes(`Your Pacific order #${orderId} has shipped`), txt.slice(0, 600));
check('admin is told no mail server is set up', txt.includes('not delivered'));
await a.evaluate((id) => [...document.querySelectorAll('details summary')].find((x) => x.textContent.includes(`#${id} has shipped`))?.click(), orderId);
await sleep(300);
check('opening an email shows its text with the tracking link', (await body(a)).includes('https://www.royalmail.com/'));
await shot(a, 'lifecycle-3-admin-emails');

await browser.close();
console.log(problems.length ? '\nPROBLEMS:\n' + [...new Set(problems)].join('\n') : '\nno console errors or failed requests');
console.log(`\n${fails} failure(s)`);
process.exit(fails || problems.length ? 1 : 0);
