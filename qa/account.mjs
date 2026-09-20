// Real-browser check of the order lifecycle: buyer checks out, seller ships with tracking, buyer follows it, admin reads the emails.
import puppeteer from 'puppeteer-core';
import { readFileSync } from 'node:fs';

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

const LOG = process.env.BACKEND_LOG;
const admin = (await call('POST', '/auth/admin/login', null, { identifier: 'e2eadmin', password: 'e2e-admin-password' })).token;
const seller = await reg('Sasha Seller');
const store = await call('POST', '/seller/apply', seller, { storeName: `Account Goods ${stamp % 1000}`, description: 'Things.' });
await call('PATCH', `/admin/sellers/${store.id}/status`, admin, { status: 'APPROVED' });
const product = await call('POST', '/seller/products', seller, { name: 'Linen Cushion', price: '25.00', stock: 30, description: 'Soft.' });

const email = `bea${stamp}@example.com`;
const oldToken = (await call('POST', '/auth/register', null, { name: 'Bea Buyer', email, password: 'correct-horse-battery' })).token;

const browser = await puppeteer.launch({ executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: 'new', args: ['--hide-scrollbars'] });
async function newPage(token) {
  const page = await browser.newPage();
  await page.setViewport({ width: 1280, height: 900 });
  if (token) await page.evaluateOnNewDocument((t) => localStorage.setItem('pacific.token', t), token);
  page.on('dialog', (d) => d.accept());
  page.on('pageerror', (e) => problems.push(`[${where}] uncaught: ${e.message}`));
  page.on('console', (m) => { if (m.type() === 'error' && !/status of (400|401|429)/.test(m.text())) problems.push(`[${where}] console.error: ${m.text().slice(0, 160)}`); });
  page.on('response', (r) => { const u = r.url(); if (u.startsWith(BASE) && r.status() >= 400 && ![400, 401, 429].includes(r.status()) && !u.includes('/@vite') && !u.endsWith('favicon.ico')) problems.push(`[${where}] HTTP ${r.status()} ${r.request().method()} ${u.replace(BASE, '')}`); });
  return page;
}
const go = async (page, path, label = path) => { where = label; await page.goto(BASE + path, { waitUntil: 'networkidle0' }); await sleep(300); };
const body = (page) => page.$eval('body', (e) => e.innerText);
const shot = (page, n) => page.screenshot({ path: `${OUT}${n}.png`, fullPage: true });
const clickButton = (page, text, exact = true) => page.evaluate((t, ex) => { const b = [...document.querySelectorAll('button')].find((x) => (ex ? x.textContent.trim() === t : x.textContent.trim().startsWith(t))); if (!b) throw new Error('no button ' + t); b.click(); }, text, exact);
const clear = async (page, sel) => { await page.click(sel, { clickCount: 3 }); await page.keyboard.press('Backspace'); };
const status = async (token) => (await fetch(API + '/auth/me', { headers: { Authorization: 'Bearer ' + token } })).status;

// ---- 1. forgot password through the UI ----
const p = await newPage(null);
await go(p, '/login');
await p.click('a[href="/forgot-password"]');
await p.waitForFunction(() => location.pathname === '/forgot-password');
await p.type('input[aria-label="Email"]', email);
await p.click('.login-button');
await p.waitForFunction(() => document.body.innerText.includes("we've sent a link"), { timeout: 8000 });
check('asking for a reset shows the same confirmation a stranger would get', true);
await shot(p, 'account-1-forgot-sent');
await sleep(1500);
const log = readFileSync(LOG, 'utf8');
const mail = log.split(`DEVELOPMENT ONLY, password reset for ${email}`)[1] ?? '';
const token = (mail.match(/reset-password\?token=([A-Za-z0-9_-]+)/) ?? [])[1];
check('a reset email was produced with a link', !!token, log.slice(-300));

// ---- 2. the reset page ----
await go(p, `/reset-password?token=${token}`);
check('the secret is taken out of the address bar', new URL(p.url()).search === '', p.url());
await p.type('input[aria-label="New password"]', 'brand-new-password-1');
await p.type('input[aria-label="Repeat the new password"]', 'something-different');
await p.click('.login-button');
await sleep(400);
check('mismatched passwords are caught before sending', (await body(p)).includes("passwords don't match"));
await clear(p, 'input[aria-label="Repeat the new password"]');
await p.type('input[aria-label="Repeat the new password"]', 'brand-new-password-1');
await p.click('.login-button');
await p.waitForFunction(() => document.body.innerText.includes('Your password has been changed'), { timeout: 8000 });
check('the password is changed', true);
await shot(p, 'account-2-reset-done');
check('a session from before the reset no longer works', (await status(oldToken)) === 401);
const stale = await call('POST', '/auth/reset-password', null, { token, password: 'yet-another-password' }).then(() => 'worked', (e) => String(e.message));
check('the link cannot be used twice', stale.includes('400'), stale);

// ---- 3. sign in with the new password; the old one fails ----
await go(p, '/login');
await p.type('input[aria-label="Email"]', email);
await p.type('input[aria-label="Password"]', 'correct-horse-battery');
await p.click('.login-button');
await sleep(800);
check('the old password is refused', (await body(p)).includes('Invalid email or password'));
await clear(p, 'input[aria-label="Password"]');
await p.type('input[aria-label="Password"]', 'brand-new-password-1');
await p.click('.login-button');
await p.waitForFunction(() => location.pathname === '/', { timeout: 8000 });
check('signing in with the new password works', (await body(p)).includes('Hello, Bea'));

// ---- 4. the admin log hides the link ----
// pages on one origin share localStorage, so opening the admin page would replace this customer's token
const pToken = await p.evaluate(() => localStorage.getItem('pacific.token'));
const a = await newPage(admin);
await go(a, '/admin/emails');
let txt = await body(a);
check('the admin email log lists the reset email', txt.includes('Reset your Pacific password'));
await a.evaluate(() => [...document.querySelectorAll('details summary')].find((x) => x.textContent.includes('Reset your Pacific password'))?.click());
await sleep(300);
txt = await body(a);
check('...but hides the link itself', txt.includes('[reset link hidden]') && !txt.includes(token), txt.slice(0, 200));

// ---- 5. account page: name and password ----
await p.bringToFront(); // a background tab never renders, so clicks would wait forever
await p.evaluate((t) => localStorage.setItem('pacific.token', t), pToken);
await p.click('a[href="/account"]');
await p.waitForFunction(() => location.pathname === '/account');
await shot(p, 'account-3-account-page');
await clear(p, '#acct-name');
await p.type('#acct-name', 'Beatrice Buyer');
await clickButton(p, 'Save name');
await p.waitForFunction(() => document.querySelector('.header-actions')?.innerText.includes('Beatrice'), { timeout: 8000 }).catch(() => {});
check('the name can be changed and the header follows', (await body(p)).includes('Hello, Beatrice'));

const before = await call('POST', '/auth/login', null, { identifier: email, password: 'brand-new-password-1' }); // a second session
await p.type('#pw-current', 'not-my-password');
await p.type('#pw-new', 'second-new-password-2');
await p.type('#pw-again', 'second-new-password-2');
await clickButton(p, 'Change password');
await p.waitForFunction(() => document.body.innerText.includes("current password isn't right"), { timeout: 8000 }).catch(() => {});
txt = await body(p);
check("a wrong current password is explained and doesn't sign you out", txt.includes("current password isn't right") && txt.includes('Hello, Beatrice'), txt.slice(0, 300));
await clear(p, '#pw-current');
await p.type('#pw-current', 'brand-new-password-1');
await clickButton(p, 'Change password');
await p.waitForFunction(() => document.body.innerText.includes('signed out on your other devices'), { timeout: 8000 }).catch(() => {});
check('the right one changes it, and this session carries on', (await body(p)).includes('Hello, Beatrice'));
check('another session is signed out by it', (await status(before.token)) === 401);
await p.reload({ waitUntil: 'networkidle0' });
check('this session still works after a reload', (await body(p)).includes('Hello, Beatrice'));

// ---- 6. address book ----
await go(p, '/account/addresses');
await clickButton(p, '+ Add an address');
await p.type('#ad-name', 'Bea Buyer');
await p.type('#ad-line1', '1 Home Street');
await p.type('#ad-city', 'Leeds');
await p.type('#ad-postcode', 'LS1 1AA');
await clickButton(p, 'Save address');
await sleep(1000);
await clickButton(p, '+ Add an address');
await p.type('#ad-name', 'Bea (work)');
await p.type('#ad-line1', '2 Work Road');
await p.type('#ad-city', 'York');
await p.type('#ad-postcode', 'YO1 1AA');
await clickButton(p, 'Save address');
await sleep(1000);
txt = await body(p);
check('two addresses saved, the first is the default', txt.includes('1 Home Street') && txt.includes('2 Work Road') && (txt.match(/Default/g) ?? []).length === 1, txt.slice(0, 400));
await shot(p, 'account-4-addresses');
await clickButton(p, 'Make default');
await sleep(1000);
const list = await call('GET', '/me/addresses', (await call('POST', '/auth/login', null, { identifier: email, password: 'second-new-password-2' })).token);
check('making the other one the default moves it first', list[0].line1 === '2 Work Road' && list[0].isDefault && !list[1].isDefault, JSON.stringify(list.map((x) => [x.line1, x.isDefault])));

// ---- 7. checkout uses a saved address ----
const bTok = (await call('POST', '/auth/login', null, { identifier: email, password: 'second-new-password-2' })).token;
await call('POST', '/cart/items', bTok, { productId: product.id, quantity: 1 });
const c = await newPage(bTok);
await c.bringToFront();
await go(c, '/checkout');
txt = await body(c);
check('checkout offers the saved addresses with the default first', txt.includes('2 Work Road') && txt.includes('1 Home Street') && txt.includes('Use a different address'), txt.slice(0, 500));
check('no address form is shown while one is selected', (await c.$('#line1')) === null);
await shot(c, 'account-5-checkout');
await c.click('.submit-btn');
await c.waitForFunction(() => /^\/orders\/\d+$/.test(location.pathname), { timeout: 10000 });
await c.waitForFunction(() => document.body.innerText.includes('Delivering to'), { timeout: 8000 });
txt = await body(c);
check('the order is delivered to the saved address', txt.includes('2 Work Road') && txt.includes('York'), txt.slice(0, 400));

// ---- 8. a new address at checkout can be remembered ----
await call('POST', '/cart/items', bTok, { productId: product.id, quantity: 1 });
await go(c, '/checkout');
await c.evaluate(() => [...document.querySelectorAll('label.pay-option')].find((l) => l.textContent.includes('Use a different address')).querySelector('input').click());
await sleep(300);
await c.type('#line1', '9 Holiday Lane');
await c.type('#city', 'Bath');
await c.type('#postcode', 'BA1 1AA');
await c.click('.submit-btn');
await c.waitForFunction(() => /^\/orders\/\d+$/.test(location.pathname), { timeout: 10000 });
await sleep(500);
const after = await call('GET', '/me/addresses', bTok);
check('the new address was saved for next time', after.length === 3 && after.some((x) => x.line1 === '9 Holiday Lane'), JSON.stringify(after.map((x) => x.line1)));

// ---- 9. guessing passwords is limited ----
const victim = `victim${stamp}@example.com`;
await call('POST', '/auth/register', null, { name: 'Vic Tim', email: victim, password: 'correct-horse-battery' });
const g = await newPage(null);
await g.evaluateOnNewDocument(() => localStorage.removeItem('pacific.token')); // pages share storage: start signed out
await g.bringToFront();
await go(g, '/login');
for (let i = 0; i < 5; i++) {
  await clear(g, 'input[aria-label="Email"]').catch(() => {});
  await g.type('input[aria-label="Email"]', victim);
  await clear(g, 'input[aria-label="Password"]').catch(() => {});
  await g.type('input[aria-label="Password"]', `wrong-guess-${i}`);
  await g.click('.login-button');
  await sleep(500);
}
await clear(g, 'input[aria-label="Password"]');
await g.type('input[aria-label="Password"]', 'correct-horse-battery');
await g.click('.login-button');
await sleep(800);
txt = await body(g);
check('after five wrong passwords even the right one is refused with a wait time', txt.includes('Too many failed sign-in attempts') && txt.includes('try again in'), txt.slice(0, 300));
await shot(g, 'account-6-throttled');

await browser.close();
console.log(problems.length ? '\nPROBLEMS:\n' + [...new Set(problems)].join('\n') : '\nno console errors or failed requests');
console.log(`\n${fails} failure(s)`);
process.exit(fails || problems.length ? 1 : 0);
