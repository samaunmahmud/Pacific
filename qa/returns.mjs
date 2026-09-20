// Real-browser check of the order lifecycle: buyer checks out, seller ships with tracking, buyer follows it, admin reads the emails.
import puppeteer from 'puppeteer-core';

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
const reg = async (name) => (await call('POST', '/auth/register', null, { name, email: `${name.split(' ')[0].toLowerCase()}${stamp}@example.com`, password: 'correct-horse-battery' })).token;

const admin = (await call('POST', '/auth/admin/login', null, { identifier: 'e2eadmin', password: 'e2e-admin-password' })).token;
const seller = await reg('Sasha Seller');
const store = await call('POST', '/seller/apply', seller, { storeName: `Returns Goods ${stamp % 1000}`, description: 'Things for the home.' });
await call('PATCH', `/admin/sellers/${store.id}/status`, admin, { status: 'APPROVED' });
const product = await call('POST', '/seller/products', seller, { name: 'Stoneware Mug Set', price: '12.00', stock: 20, description: 'Four mugs.' });
const buyer = await reg('Bea Buyer');
// a delivered card order of 3 mugs (36.00 + 3.99 delivery), set up over the API
await call('POST', '/cart/items', buyer, { productId: product.id, quantity: 3 });
const co = await call('POST', '/orders', buyer, { name: 'Bea Buyer', line1: '1 Test Street', city: 'London', postcode: 'N1 1AA', country: 'United Kingdom', paymentMethod: 'CARD' });
const orderId = co.orders[0].id;
await call('POST', `/payments/${co.checkoutRef}/simulate`, buyer, { outcome: 'PAID' });
for (const st of ['PROCESSING', 'SHIPPED', 'DELIVERED']) await call('PATCH', `/seller/orders/${orderId}/status`, seller, { status: st });

const browser = await puppeteer.launch({ executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: 'new', args: ['--hide-scrollbars'] });
async function newPage(token) {
  const page = await browser.newPage();
  await page.setViewport({ width: 1280, height: 900 });
  await page.evaluateOnNewDocument((t) => localStorage.setItem('pacific.token', t), token);
  page.on('dialog', (d) => d.accept());
  page.on('pageerror', (e) => problems.push(`[${where}] uncaught: ${e.message}`));
  page.on('console', (m) => { if (m.type() === 'error' && !/status of 400/.test(m.text())) problems.push(`[${where}] console.error: ${m.text().slice(0, 160)}`); });
  page.on('response', (r) => { const u = r.url(); if (u.startsWith(BASE) && r.status() >= 400 && r.status() !== 400 && !u.includes('/@vite') && !u.endsWith('favicon.ico')) problems.push(`[${where}] HTTP ${r.status()} ${r.request().method()} ${u.replace(BASE, '')}`); });
  return page;
}
const go = async (page, path, label = path) => { where = label; await page.goto(BASE + path, { waitUntil: 'networkidle0' }); await sleep(300); };
const body = (page) => page.$eval('body', (e) => e.innerText);
const shot = (page, n) => page.screenshot({ path: `${OUT}${n}.png`, fullPage: true });
const clickButton = (page, text, exact = false) => page.evaluate((t, ex) => { const b = [...document.querySelectorAll('button')].find((x) => (ex ? x.textContent.trim() === t : x.textContent.trim().startsWith(t))); if (!b) throw new Error('no button ' + t); b.click(); }, text, exact);
const earnings = async () => call('GET', '/seller/earnings', seller);

// ---- 1. buyer asks to return one mug ----
const b = await newPage(buyer);
await go(b, `/orders/${orderId}`);
let txt = await body(b);
check('delivered order offers a return with its deadline', txt.includes('Returns & refunds') && txt.includes('Return items') && txt.includes('You can return items until'), txt.slice(0, 200));
await clickButton(b, 'Return items');
await sleep(300);
const rq = await b.$('select[id^="rq-"]');
await rq.select('1');
await b.select('#return-reason', 'DAMAGED');
await b.type('#return-comment', 'One handle snapped in the box');
await shot(b, 'returns-1-form');
await clickButton(b, 'Send return request');
await b.waitForFunction(() => document.body.innerText.includes('Return #'), { timeout: 8000 });
await sleep(500);
txt = await body(b);
check('the request shows as "Return requested" with the seller to answer', txt.includes('Return requested') && txt.includes('The seller will look at your request'), txt.slice(0, 500));
const ret = (await call('GET', `/orders/${orderId}`, buyer)).returns[0];

// ---- 2. seller sees it on the dashboard, approves ----
const s = await newPage(seller);
await go(s, '/seller');
check('dashboard tells the seller a return is waiting', (await body(s)).includes('return request waiting for your decision'));
await go(s, '/seller/returns');
txt = await body(s);
check('returns page lists it under "Awaiting your decision"', txt.includes(`Return #${ret.id}`) && txt.includes('One handle snapped in the box') && txt.includes('Arrived damaged or faulty'), txt.slice(0, 400));
await s.type(`#note-${ret.id}`, 'Post it to 9 Depot Road, Leeds');
await shot(s, 'returns-2-seller-decide');
await clickButton(s, 'Approve return');
await sleep(1000);
check('approving moves it to the refund step for a card order', (await body(s)).includes('goes back to the customer') && (await s.$(`#amt-${ret.id}`)) !== null);
check('the refund amount is prefilled with the goods only (12.00)', (await s.$eval(`#amt-${ret.id}`, (e) => e.value)) === '12.00');

// ---- 3. seller refunds; a bigger amount is refused ----
await s.click(`#amt-${ret.id}`, { clickCount: 3 });
await s.type(`#amt-${ret.id}`, '50');
await clickButton(s, 'Items received: refund');
await sleep(800);
check('an amount above the maximum is refused before anything is sent', (await body(s)).includes('Enter an amount up to'));
await s.click(`#amt-${ret.id}`, { clickCount: 3 });
await s.type(`#amt-${ret.id}`, '12');
await clickButton(s, 'Items received: refund');
await s.waitForFunction(() => document.body.innerText.includes('refunded to the card'), { timeout: 8000 });
check('the refund is issued to the card', true);
await shot(s, 'returns-3-seller-refunded');

// ---- 4. money and ledger ----
const pay = await call('GET', `/payments/${co.checkoutRef}`, buyer);
check('the payment shows exactly 12.00 refunded', pay.refundedAmount === 12, pay.refundedAmount);
const e = await earnings();
check('seller earnings show the refund and commission returned', e.refunds === 12 && e.entries.items.some((x) => x.type === 'REFUND') && e.entries.items.some((x) => x.type === 'COMMISSION_REFUND'), JSON.stringify(e).slice(0, 300));
await go(s, '/seller/earnings');
txt = await body(s);
check('earnings page shows "Refunded to customers" and "Commission returned"', txt.includes('Refunded to customers') && txt.includes('Commission returned'), txt.slice(0, 300));
await shot(s, 'returns-4-earnings');

// ---- 5. buyer sees the refund; can still return the other two ----
await go(b, `/orders/${orderId}`);
txt = await body(b);
check('buyer sees the £12.00 refunded to their card', txt.includes('£12.00 refunded') && txt.includes('to your card'), txt.slice(0, 600));
check('the activity timeline records the return and refund', txt.includes('Return requested') && txt.includes('Return approved') && txt.includes('Refunded'));
check('buyer can still return the remaining two', txt.includes('Return items'));
await shot(b, 'returns-5-buyer-after');

// ---- 6. a second request, declined with a reason ----
const second = await call('POST', `/orders/${orderId}/returns`, buyer, { items: [{ orderItemId: (await call('GET', `/orders/${orderId}`, buyer)).items[0].id, quantity: 2 }], reason: 'NO_LONGER_NEEDED', comment: null });
await go(s, '/seller/returns');
await clickButton(s, 'Decline', true);
await sleep(600);
check('declining without a reason is refused', (await body(s)).includes('Please tell the customer why'));
await s.type(`#note-${second.id}`, 'Sorry, these were personalised');
await clickButton(s, 'Decline', true);
await sleep(1000);
await go(b, `/orders/${orderId}`);
txt = await body(b);
check('buyer sees it declined with the seller\'s reason', txt.includes('Declined') && txt.includes('Sorry, these were personalised'), txt.slice(0, 400));

// ---- 7. admin can see every return ----
const a = await newPage(admin);
await go(a, '/admin/returns?status=ALL');
txt = await body(a);
check('admin sees all returns with the seller named', txt.includes(`Return #${ret.id}`) && txt.includes(`Return #${second.id}`) && txt.includes('Returns Goods'), txt.slice(0, 300));

await browser.close();
console.log(problems.length ? '\nPROBLEMS:\n' + [...new Set(problems)].join('\n') : '\nno console errors or failed requests');
console.log(`\n${fails} failure(s)`);
process.exit(fails || problems.length ? 1 : 0);
