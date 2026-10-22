// The admin dashboard's marketplace sales panel in a real browser: two new stores each sell a high-priced piece, then
// the panel adds them to everyone else's sales, names the store of each best seller, links to the admin product page,
// switches period, and holds up in the dark theme and on a phone.
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
const n = stamp % 10000;
const admin = (await call('POST', '/auth/admin/login', null, { identifier: 'e2eadmin', password: 'e2e-admin-password' })).token;
async function customer(name) {
  const email = `${name.split(' ')[0].toLowerCase()}${stamp}@example.com`;
  const reg = await call('POST', '/auth/register', null, { name, email, password: 'correct-horse-battery' });
  await confirmEmail(email);
  return reg;
}
async function store(owner, name) {
  const s = await call('POST', '/seller/apply', owner.token, { storeName: name, description: 'Fine art.' });
  await call('PATCH', `/admin/sellers/${s.id}/status`, admin, { status: 'APPROVED' });
}
const address = { name: 'Cal Collector', line1: '1 Road', city: 'Town', postcode: 'AB1 2CD', country: 'UK' };

const before = await call('GET', '/admin/insights?days=30', admin);
const north = await customer('Nora North');
const south = await customer('Sam South');
await store(north, `Gallery North ${n}`);
await store(south, `Gallery South ${n}`);
const statue = await call('POST', '/seller/products', north.token, { name: `Marble Statue ${n}`, price: '9000.00', stock: 3 });
const plinth = await call('POST', '/seller/products', south.token, { name: `Oak Plinth ${n}`, price: '8000.00', stock: 3 });
const buyer = await customer('Cal Collector');
await call('POST', '/cart/items', buyer.token, { productId: statue.id, quantity: 1 });
await call('POST', '/cart/items', buyer.token, { productId: plinth.id, quantity: 1 });
const placed = (await call('POST', '/orders', buyer.token, address)).orders;
const expectedSales = Number(before.current.sales) + placed.reduce((sum, o) => sum + Number(o.total), 0);
const expectedOrders = before.current.orders + placed.length;

const browser = await puppeteer.launch({ executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: 'new', args: ['--hide-scrollbars'] });
const tab = await browser.newPage();
await tab.setViewport({ width: 1440, height: 900 });
tab.on('pageerror', (e) => problems.push(`[${where}] uncaught: ${e.message}`));
tab.on('console', (m) => { if (m.type() === 'error') problems.push(`[${where}] console.error: ${m.text().slice(0, 160)}`); });
tab.on('response', (r) => { const u = r.url(); if (u.startsWith(BASE) && r.status() >= 400 && !u.includes('/@vite')) problems.push(`[${where}] HTTP ${r.status()} ${r.request().method()} ${u.replace(BASE, '')}`); });
async function go(path, label = path) { where = label; await tab.goto(BASE + path, { waitUntil: 'networkidle0' }); await sleep(400); }
async function as(token, theme = 'light') {
  await tab.goto(BASE + '/admin/login', { waitUntil: 'domcontentloaded' });
  await tab.evaluate((t, th) => { localStorage.setItem('pacific.token', t); localStorage.setItem('pacific-theme', th); }, token, theme);
}
async function shot(name, sel) {
  const el = sel ? await tab.$(sel) : null;
  if (el) await el.screenshot({ path: `${OUT}${name}.png` }); else await tab.screenshot({ path: `${OUT}${name}.png`, fullPage: true });
}
const text = (sel) => tab.$eval(sel, (e) => e.textContent).catch(() => '');
const gbp = (x) => new Intl.NumberFormat('en-GB', { style: 'currency', currency: 'GBP' }).format(x);

// ---------- the figures ----------
await as(admin);
await go('/admin', 'admin dashboard');
check('the admin dashboard shows the sales panel', !!(await tab.$('.ins-panel')));
check('...described as every store’s orders', (await text('.ins-sub')).startsWith('Every store'), await text('.ins-sub'));
const figures = await tab.$$eval('.ins-figure', (els) => els.map((e) => e.textContent));
check('sales add the new stores to everyone else’s', figures[0]?.includes(gbp(expectedSales)), `${figures[0]} vs ${gbp(expectedSales)}`);
check('...and so do the orders', figures[1]?.includes(String(expectedOrders)), `${figures[1]} vs ${expectedOrders}`);

// ---------- best sellers across stores ----------
const top = await tab.$$eval('.ins-top li', (els) => els.map((e) => e.textContent));
check('the dearest pieces lead the best sellers, statue first', top[0]?.includes('Marble Statue') && top[1]?.includes('Oak Plinth'), top);
const stores = await tab.$$eval('.ins-store', (els) => els.map((e) => e.textContent));
check('...each named with its store', stores[0] === `Gallery North ${n}` && stores[1] === `Gallery South ${n}`, stores);
await shot('qa-admin-insights-light', '.ins-panel');
await tab.click('.ins-top li:first-child a');
await sleep(800);
check('a best seller links to its admin product page', tab.url().endsWith(`/admin/products/${statue.id}`), tab.url());

// ---------- the period switch ----------
await go('/admin', 'admin dashboard again');
await tab.click('.ins-seg button:nth-child(1)');
await sleep(800);
check('switching to 7 days relabels the comparison', (await text('.ins-figure')).includes('vs previous 7 days'));

// ---------- dark theme and phone ----------
await as(admin, 'dark');
await go('/admin', 'admin dark');
const panelBg = await tab.$eval('.ins-panel', (e) => getComputedStyle(e).backgroundColor);
check('the dark theme gives the panel a dark surface', panelBg !== 'rgb(255, 255, 255)', panelBg);
await shot('qa-admin-insights-dark', '.ins-panel');

await tab.setViewport({ width: 390, height: 844, isMobile: true, hasTouch: true });
await as(admin);
await go('/admin', 'admin phone');
const overflow = await tab.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
check('the phone layout has no sideways scroll', overflow <= 0, overflow);
await shot('qa-admin-insights-phone', '.ins-panel');

// ---------- the endpoint is the admin's alone ----------
const shut = await fetch(API + '/admin/insights', { headers: { Authorization: 'Bearer ' + north.token } });
check('a seller cannot read the marketplace figures', shut.status === 403, shut.status);

await browser.close();
problems.forEach((p) => console.log('PROBLEM ' + p));
const failed = results.filter((r) => !r).length + problems.length;
console.log(`\n${results.length - results.filter((r) => !r).length}/${results.length} checks passed, ${problems.length} page problems`);
process.exit(failed ? 1 : 0);
