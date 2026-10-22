// Seller Central's sales panel in a real browser: a new store sells three products to two shoppers (one order is
// cancelled), then the dashboard shows the figures and their change, the daily chart with a tooltip, the best sellers
// ranked by revenue, the 7/30/90-day switch, the order pipeline with links, the dark theme and a phone layout.
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
const address = { name: 'Bea Buyer', line1: '1 Road', city: 'Town', postcode: 'AB1 2CD', country: 'UK' };
async function buy(token, lines) {
  for (const [id, quantity] of lines) await call('POST', '/cart/items', token, { productId: id, quantity });
  return (await call('POST', '/orders', token, address)).orders[0];
}

const seller = await customer('Iris Insight');
const store = await call('POST', '/seller/apply', seller.token, { storeName: `Insight Pottery ${stamp % 10000}`, description: 'Handmade.' });
await call('PATCH', `/admin/sellers/${store.id}/status`, admin, { status: 'APPROVED' });
const n = stamp % 10000;
const bowl = await call('POST', '/seller/products', seller.token, { name: `Insight Bowl ${n}`, price: '12.00', stock: 30 });
const vase = await call('POST', '/seller/products', seller.token, { name: `Insight Vase ${n}`, price: '30.00', stock: 30 });
const cup = await call('POST', '/seller/products', seller.token, { name: `Insight Cup ${n}`, price: '8.00', stock: 30 });
const ann = await customer('Ann Shopper');
const ben = await customer('Ben Shopper');
const o1 = await buy(ann.token, [[bowl.id, 3], [cup.id, 1]]); // £36 + £8
const o2 = await buy(ben.token, [[vase.id, 2]]); // £60
const o3 = await buy(ben.token, [[cup.id, 5]]); // cancelled: never counts
await call('PATCH', `/seller/orders/${o3.id}/status`, seller.token, { status: 'CANCELLED' });
const expectedSales = Number(o1.total) + Number(o2.total);

const browser = await puppeteer.launch({ executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: 'new', args: ['--hide-scrollbars'] });
const tab = await browser.newPage();
await tab.setViewport({ width: 1440, height: 900 });
tab.on('pageerror', (e) => problems.push(`[${where}] uncaught: ${e.message}`));
tab.on('console', (m) => { if (m.type() === 'error') problems.push(`[${where}] console.error: ${m.text().slice(0, 160)}`); });
tab.on('response', (r) => { const u = r.url(); if (u.startsWith(BASE) && r.status() >= 400 && !u.includes('/@vite')) problems.push(`[${where}] HTTP ${r.status()} ${r.request().method()} ${u.replace(BASE, '')}`); });
async function go(path, label = path) { where = label; await tab.goto(BASE + path, { waitUntil: 'networkidle0' }); await sleep(400); }
async function as(token, theme = 'light') {
  await tab.goto(BASE + '/login', { waitUntil: 'domcontentloaded' });
  await tab.evaluate((t, th) => { localStorage.setItem('pacific.token', t); localStorage.setItem('pacific-theme', th); }, token, theme);
}
async function shot(name, sel) {
  const el = sel ? await tab.$(sel) : null;
  if (el) await el.screenshot({ path: `${OUT}${name}.png` }); else await tab.screenshot({ path: `${OUT}${name}.png`, fullPage: true });
}
const text = (sel) => tab.$eval(sel, (e) => e.textContent).catch(() => '');
const gbp = (x) => new Intl.NumberFormat('en-GB', { style: 'currency', currency: 'GBP' }).format(x);

// ---------- the figures ----------
await as(seller.token);
await go('/seller', 'seller central');
const figures = await tab.$$eval('.ins-figure', (els) => els.map((e) => e.textContent));
check('the sales panel shows sales, orders, units and the average order', figures.length === 4, figures);
check('...sales count the two live orders, not the cancelled one', figures[0]?.includes(gbp(expectedSales)), `${figures[0]} vs ${gbp(expectedSales)}`);
check('...2 orders and 6 units', figures[1]?.includes('2') && figures[2]?.includes('6'), figures);
check('...a first month of sales reads as new', figures[0]?.includes('new') && figures[0]?.includes('vs previous 30 days'), figures[0]);
check('30 days is chosen at first', (await tab.$eval('.ins-seg button[aria-pressed="true"]', (b) => b.textContent)) === '30 days');

// ---------- the chart ----------
const box = await (await tab.$('.ins-plot svg')).boundingBox();
await tab.mouse.move(box.x + box.width - 2, box.y + box.height / 2);
await sleep(200);
const tip = await text('.ins-tip');
check('hovering the last day shows its takings and orders', tip.includes(gbp(expectedSales)) && tip.includes('2 orders'), tip);
check('the chart has an accessible description', ((await tab.$eval('.ins-plot svg', (s) => s.getAttribute('aria-label'))) || '').startsWith('Sales per day'));
await tab.mouse.move(box.x + 4, box.y + box.height / 2);
await sleep(150);
check('...and an earlier day shows nothing sold', (await text('.ins-tip')).includes('0 orders'), await text('.ins-tip'));

// ---------- best sellers ----------
const top = await tab.$$eval('.ins-top li', (els) => els.map((e) => e.textContent));
check('best sellers are ranked by revenue: vase, bowl, cup', top.length === 3 && top[0].includes('Vase') && top[1].includes('Bowl') && top[2].includes('Cup'), top);
check('...with what each brought in and how many sold', top[0].includes('£60.00') && top[0].includes('2 sold') && top[2].includes('1 sold'), top);
await shot('qa-insights-light', '.ins-panel');

// ---------- the period switch ----------
await tab.click('.ins-seg button:nth-child(1)');
await sleep(800);
check('switching to 7 days refetches and relabels the comparison', (await text('.ins-figure')).includes('vs previous 7 days') && (await tab.$eval('.ins-seg button[aria-pressed="true"]', (b) => b.textContent)) === '7 days');
await tab.click('.ins-seg button:nth-child(3)');
await sleep(800);
check('...and to 90 days', (await text('.ins-figure')).includes('vs previous 90 days'));

// ---------- the pipeline ----------
const legend = await tab.$$eval('.pipe-item', (els) => els.map((e) => e.textContent));
check('the order pipeline counts each status', legend.some((l) => l.startsWith('Placed') && l.endsWith('2')) && legend.some((l) => l.startsWith('Cancelled') && l.endsWith('1')), legend);
check('...with one bar segment per status that has orders', (await tab.$$('.pipe-seg')).length === 2);
await tab.click('.pipe-item');
await sleep(800);
check('...and each status links to its orders', tab.url().includes('/seller/orders?status=PLACED'), tab.url());

// ---------- dark theme and phone ----------
await as(seller.token, 'dark');
await go('/seller', 'seller central dark');
check('the dark theme is on', (await tab.evaluate(() => document.documentElement.dataset.theme)) === 'dark');
const panelBg = await tab.$eval('.ins-panel', (e) => getComputedStyle(e).backgroundColor);
check('...and the panel uses the dark surface', panelBg !== 'rgb(255, 255, 255)', panelBg);
await shot('qa-insights-dark', '.ins-panel');

await tab.setViewport({ width: 390, height: 844, isMobile: true, hasTouch: true });
await as(seller.token);
await go('/seller', 'seller central phone');
const overflow = await tab.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
check('the phone layout has no sideways scroll', overflow <= 0, overflow);
check('...and shows the figures two by two', (await tab.$eval('.ins-figures', (e) => getComputedStyle(e).gridTemplateColumns.split(' ').length)) === 2);
await shot('qa-insights-phone', '.ins-panel');

await browser.close();
problems.forEach((p) => console.log('PROBLEM ' + p));
const failed = results.filter((r) => !r).length + problems.length;
console.log(`\n${results.length - results.filter((r) => !r).length}/${results.length} checks passed, ${problems.length} page problems`);
process.exit(failed ? 1 : 0);
