// Variations in a real browser: a seller sets up colours and sizes in Seller Central; search shows one card with
// "See options"; the product page's picker switches colour (keeping the size) and size, marks sold-out and missing
// combinations; the cart says which variation was bought; demo clothes show colour pictures; phone layouts fit.
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
const tag = `vx${stamp % 1000000}`;
const admin = (await call('POST', '/auth/admin/login', null, { identifier: 'e2eadmin', password: 'e2e-admin-password' })).token;
async function customer(name) {
  const email = `${name.split(' ')[0].toLowerCase()}${stamp}@example.com`;
  const reg = await call('POST', '/auth/register', null, { name, email, password: 'correct-horse-battery' });
  await confirmEmail(email);
  return reg;
}
const seller = await customer('Vera Variant');
const store = await call('POST', '/seller/apply', seller.token, { storeName: `Variant Wear ${stamp % 10000}`, description: 'x' });
await call('PATCH', `/admin/sellers/${store.id}/status`, admin, { status: 'APPROVED' });
const tee = await call('POST', '/seller/products', seller.token, { name: `Linen Tee ${tag}`, price: '12.00', stock: 10, description: 'Breathable linen' });
const shopper = await customer('Sam Shopper');

const browser = await puppeteer.launch({ executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: 'new', args: ['--hide-scrollbars'] });
const tab = await browser.newPage();
await tab.setViewport({ width: 1440, height: 900 });
tab.on('pageerror', (e) => problems.push(`[${where}] uncaught: ${e.message}`));
tab.on('console', (m) => { if (m.type() === 'error') problems.push(`[${where}] console.error: ${m.text().slice(0, 160)}`); });
tab.on('response', (r) => { const u = r.url(); if (u.startsWith(BASE) && r.status() >= 400 && !u.includes('/@vite')) problems.push(`[${where}] HTTP ${r.status()} ${r.request().method()} ${u.replace(BASE, '')}`); });
async function go(path, label = path) { where = label; await tab.goto(BASE + path, { waitUntil: 'networkidle0' }); await sleep(400); }
async function shot(name) { await tab.screenshot({ path: `${OUT}${name}.png`, fullPage: true }); }
const text = (sel) => tab.$eval(sel, (e) => e.textContent).catch(() => '');
async function signIn(token) {
  await tab.goto(BASE + '/login', { waitUntil: 'domcontentloaded' });
  await tab.evaluate((t) => localStorage.setItem('pacific.token', t), token);
}
async function fill(sel, value) {
  await tab.click(sel, { clickCount: 3 });
  await tab.keyboard.press('Backspace');
  await tab.type(sel, value);
}
async function addVariation(o1, o2, price, stock) {
  await fill('#vn1', o1);
  await fill('#vn2', o2);
  await fill('#vnp', price);
  await fill('#vns', String(stock));
  await tab.click('form[aria-label="Add a variation"] .submit-btn');
  await tab.waitForFunction((n) => document.querySelectorAll('.var-table tbody tr').length === n, { timeout: 5000 },
    (await tab.$$('.var-table tbody tr')).length + 1).catch(() => {});
}
const pickerPath = () => new URL(tab.url()).pathname;

// ---------- Seller Central ----------
await signIn(seller.token);
await go(`/seller/products/${tee.id}`, 'seller: edit tee');
check('the product form offers variations', (await text('.variations-panel')).includes('Sell this in other colours'), await text('.variations-panel'));
await fill('#vd1', 'Colour');
await fill('#vd2', 'Size');
await fill('#vo1', 'Red');
await fill('#vo2', 'M');
await tab.click('form[aria-label="What the variations differ by"] .submit-btn');
await tab.waitForSelector('.var-table', { timeout: 5000 }).catch(() => {});
check('setting up variations lists this one', (await text('.var-table')).includes('Red / M'), await text('.var-table'));
await addVariation('Red', 'L', '13.00', 4);
await addVariation('Blue', 'M', '12.00', 0);
await addVariation('Blue', 'L', '13.00', 3);
const rows = await tab.$$eval('.var-table tbody tr', (trs) => trs.map((t) => t.textContent));
check('three more variations are added', rows.length === 4 && rows[3].includes('Blue / L') && rows[2].includes('Sold out'), JSON.stringify(rows));
await fill('#vn1', 'red');
await fill('#vn2', 'l');
await tab.click('form[aria-label="Add a variation"] .submit-btn');
await sleep(800);
check('a repeated option is refused with a clear message', (await text('.variations-panel .notice.error')).includes('already a variation'), await text('.variations-panel .notice.error'));
problems.splice(0, problems.length, ...problems.filter((p) => !p.includes('409'))); // the refusal itself
await shot('qa-variations-seller');
const fam = await call('GET', `/seller/products/${tee.id}`, seller.token);
const idOf = (o1, o2) => fam.variations.options.find((o) => o.option1 === o1 && o.option2 === o2).productId;
const [redL, blueM, blueL] = [idOf('Red', 'L'), idOf('Blue', 'M'), idOf('Blue', 'L')];
await go('/seller/products', 'seller: product list');
check('the product list names each variation', (await text('table.data')).includes('Colour: Blue, Size: L'), await text('table.data'));

// ---------- shopper: search and the picker ----------
await signIn(shopper.token);
await go(`/products?q=${tag}`, 'search');
const cards = await tab.$$eval('.product-card', (els) => els.map((e) => e.textContent));
check('search shows one card for all four', cards.length === 1 && cards[0].includes('4 options available') && cards[0].includes('See options'), JSON.stringify(cards));
await tab.click('.product-card');
await tab.waitForSelector('.variations', { timeout: 5000 }).catch(() => {});
check('the card opens the product with a colour and size picker', (await text('.variations')).includes('Colour: Red') && (await text('.variations')).includes('Size: M'), await text('.variations'));
await shot('qa-variations-pdp');

await tab.evaluate(() => [...document.querySelectorAll('.var-size')].find((b) => b.textContent === 'L').click());
await tab.waitForFunction((id) => location.pathname === `/products/${id}`, { timeout: 5000 }, redL).catch(() => {});
await sleep(600);
const boxPrice = await tab.$eval('.buy-box .money-big', (e) => e.getAttribute('aria-label')).catch(() => '');
check('choosing size L opens Red / L at its own price', pickerPath() === `/products/${redL}` && boxPrice === '£13.00', `${pickerPath()} ${boxPrice}`);
await tab.evaluate(() => [...document.querySelectorAll('.var-swatch')].find((b) => b.textContent.includes('Blue')).click());
await tab.waitForFunction((id) => location.pathname === `/products/${id}`, { timeout: 5000 }, blueL).catch(() => {});
await sleep(600);
check('choosing Blue keeps size L', pickerPath() === `/products/${blueL}`, pickerPath());
const mButton = await tab.evaluate(() => { const b = [...document.querySelectorAll('.var-size')].find((x) => x.textContent === 'M'); return b ? b.className : ''; });
check('Blue / M shows as sold out', mButton.includes('unavailable'), mButton);
await tab.evaluate(() => [...document.querySelectorAll('.var-size')].find((b) => b.textContent === 'M').click());
await sleep(1000);
check('a sold-out variation still opens, as currently unavailable', pickerPath() === `/products/${blueM}` && (await text('.buy-box')).includes('Currently unavailable'), `${pickerPath()} ${await text('.bb-stock')}`);
await go(`/products/${blueL}`, 'blue L');
await tab.click('.buy-box .cart-btn');
await sleep(1200);
await go('/cart', 'cart');
check('the cart says which variation', (await text('.cart-item')).includes('Colour: Blue, Size: L'), await text('.cart-item'));

// ---------- a missing combination ----------
await call('DELETE', `/seller/products/${blueM}/family`, seller.token);
await go(`/products/${blueL}`, 'blue L after removing blue M');
const mState = await tab.evaluate(() => { const b = [...document.querySelectorAll('.var-size')].find((x) => x.textContent === 'M'); return b ? { disabled: b.disabled, title: b.title } : null; });
check('a combination that doesn\'t exist is crossed out', !!mState && mState.disabled && mState.title === 'Not available in Blue', JSON.stringify(mState));

// ---------- demo clothes: colour pictures ----------
const fashion = await call('GET', '/products?category=fashion&size=48');
const family = fashion.items.find((p) => p.variationCount >= 9);
if (family) {
  await go(`/products/${family.id}`, 'demo fashion');
  const thumbs = await tab.$$('.var-swatch .var-thumb');
  check('demo clothes show a picture for each colour', thumbs.length === 3, `${thumbs.length}`);
  await shot('qa-variations-demo');
} else check('demo clothes have colours and sizes', false, 'no family with 9 variations in Fashion');

// ---------- phone ----------
await tab.setViewport({ width: 390, height: 844 });
await go(`/products/${blueL}`, 'pdp (phone)');
let overflow = await tab.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
check('the picker fits a phone', overflow <= 0, `overflows by ${overflow}px`);
await shot('qa-variations-phone');
await signIn(seller.token);
await go(`/seller/products/${tee.id}`, 'seller panel (phone)');
overflow = await tab.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
check('the variations panel fits a phone', overflow <= 0, `overflows by ${overflow}px`);

console.log('\n=== problems seen in the browser ===');
const unique = [...new Set(problems)];
console.log(unique.length ? unique.join('\n') : 'none');
await browser.close();
const failed = results.filter((r) => !r).length;
console.log(`\n${results.length - failed}/${results.length} checks passed, ${unique.length} distinct browser problem(s)`);
process.exit(failed || unique.length ? 1 : 0);
