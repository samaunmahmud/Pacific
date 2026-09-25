// Email confirmation in a real browser: a customer signs up through the form, is reminded to confirm, can shop but
// not order or apply to sell, opens the link from the email (read from the backend's output, like account.mjs) and
// can then order. Also: opening the link twice, a broken link, "send it again" and its limit, the admin email log
// hiding the link, an admin confirming a customer by hand, and the phone layout.
import puppeteer from 'puppeteer-core';
import { readFileSync } from 'node:fs';
import { confirmEmail } from './confirm.mjs';

const BASE = process.env.QA_BASE || 'http://localhost:5180';
const OUT = new URL('./shots/', import.meta.url).pathname;
const API = BASE + '/api';
const LOG = process.env.BACKEND_LOG;
if (!LOG) { console.error('Set BACKEND_LOG to the file the backend writes its output to.'); process.exit(2); }
const results = [];
const problems = [];
let where = 'start';
let expecting = []; // HTTP statuses this step provokes on purpose

const check = (name, ok, detail = '') => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + name + (ok ? '' : `  [${String(detail).slice(0, 300)}]`)); };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
async function call(method, url, token, body) {
  const r = await fetch(API + url, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) }, body: body ? JSON.stringify(body) : undefined });
  const t = await r.text();
  return t ? JSON.parse(t) : null;
}
/** The newest confirmation link the backend logged for this address (development only). */
function linkFor(email) {
  const mail = readFileSync(LOG, 'utf8').split(`DEVELOPMENT ONLY, email confirmation for ${email}`).at(-1) ?? '';
  return (mail.match(/verify-email\?token=([A-Za-z0-9_-]+)/) ?? [])[1];
}

const browser = await puppeteer.launch({ executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: 'new', args: ['--hide-scrollbars'] });
const page = await browser.newPage();
await page.setViewport({ width: 1440, height: 900 });
page.on('pageerror', (e) => problems.push(`[${where}] uncaught: ${e.message}`));
page.on('console', (m) => { if (m.type() === 'error' && !expecting.some((s) => m.text().includes(String(s)))) problems.push(`[${where}] console.error: ${m.text().slice(0, 160)}`); });
page.on('response', (r) => {
  const u = r.url();
  if (u.startsWith(BASE) && r.status() >= 400 && !expecting.includes(r.status()) && !u.includes('/@vite')) problems.push(`[${where}] HTTP ${r.status()} ${r.request().method()} ${u.replace(BASE, '')}`);
});
async function go(path, label = path) {
  where = label;
  await page.goto(BASE + path, { waitUntil: 'networkidle0' });
  await sleep(300);
}
async function shot(name) { await page.screenshot({ path: `${OUT}${name}.png`, fullPage: true }); }
const text = (sel) => page.$eval(sel, (e) => e.textContent).catch(() => '');
async function clickText(sel, contains) {
  for (const h of await page.$$(sel)) if ((await h.evaluate((e) => e.textContent)).includes(contains)) { await h.click(); return true; }
  return false;
}

// ---------- sign up through the form ----------
const stamp = Date.now();
const email = `vera${stamp}@example.com`;
await go('/register');
await page.type('input[aria-label="Full name"]', 'Vera Verify');
await page.type('input[aria-label="Email"]', email);
await page.type('input[aria-label="Password"]', 'correct-horse-battery');
await page.click('.login-button');
await page.waitForFunction(() => location.pathname === '/', { timeout: 8000 }).catch(() => {});
await sleep(600);
check('a new customer sees a reminder to confirm, naming their email', (await text('.confirm-email.banner')).includes(email), await text('.confirm-email'));
await shot('qa-verify-banner');
const token = (await page.evaluate(() => localStorage.getItem('pacific.token')));

// ---------- shopping works; ordering and selling wait ----------
const product = (await call('GET', '/products?size=5&sort=popular')).items.find((p) => p.stock > 3);
await call('POST', '/cart/items', token, { productId: product.id, quantity: 1 });
await go('/checkout', 'checkout (unconfirmed)');
check('checkout explains why the order can\'t be placed yet', (await text('.confirm-email')).includes('Confirm your email to place your order'), await text('.confirm-email'));
check('...and the order button is disabled', await page.$eval('form .submit-btn', (b) => b.disabled));
check('...without repeating the site-wide reminder', (await page.$('.confirm-email.banner')) === null);
await shot('qa-verify-checkout');
await go('/sell', 'sell (unconfirmed)');
check('applying to sell waits for confirmation too', (await text('.confirm-email')).includes('Confirm your email to apply to sell') && (await page.$('.app-main form')) === null, await text('.app-main'));
expecting = [403];
const refused = await fetch(API + '/orders', { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token }, body: JSON.stringify({ name: 'V', line1: '1 St', city: 'X', postcode: 'UB8 1AA', country: 'UK' }) });
check('the API refuses an order from an unconfirmed customer', refused.status === 403, refused.status);
expecting = [];

// ---------- "send it again", and not too often ----------
const firstLink = linkFor(email);
check('the sign-up email carried a confirmation link', !!firstLink, readFileSync(LOG, 'utf8').slice(-300));
await go('/', 'home (resend)');
expecting = [429];
await clickText('.confirm-email button', 'Send it again');
await sleep(800);
const sentAgain = await text('.confirm-email');
// the sign-up email went out moments ago, so the shop asks to wait a minute
check('asking again straight after sign-up is refused politely', sentAgain.includes('wait a minute'), sentAgain);
expecting = [];

// ---------- open the link ----------
await go(`/verify-email?token=${firstLink}`, 'verify page');
await page.waitForFunction(() => document.querySelector('.auth-form .notice.ok, .auth-form .notice.error'), { timeout: 8000 }).catch(() => {});
check('opening the link confirms the email', (await text('.auth-form')).includes('your email is confirmed'), await text('.auth-form'));
check('the token is removed from the address bar', !page.url().includes('token='), page.url());
await shot('qa-verify-done');
await go(`/verify-email?token=${firstLink}`, 'verify page again');
await page.waitForFunction(() => document.querySelector('.auth-form .notice.ok, .auth-form .notice.error'), { timeout: 8000 }).catch(() => {});
check('opening the same link again is harmless', (await text('.auth-form')).includes('your email is confirmed'), await text('.auth-form'));
expecting = [400];
await go('/verify-email?token=not-a-real-token', 'verify page (broken link)');
await page.waitForFunction(() => document.querySelector('.auth-form .notice.error'), { timeout: 8000 }).catch(() => {});
check('a broken link says so', (await text('.auth-form')).includes('invalid or has expired'), await text('.auth-form'));
expecting = [];

await go('/', 'home (confirmed)');
check('the reminder is gone once confirmed', (await page.$('.confirm-email')) === null);
await go('/checkout', 'checkout (confirmed)');
await page.waitForSelector('#name', { timeout: 8000 });
await page.type('#name', 'Vera Verify');
await page.type('#line1', '10 Test Street');
await page.type('#city', 'Uxbridge');
await page.type('#postcode', 'UB8 1AA');
await page.click('form .submit-btn');
await page.waitForFunction(() => /^\/orders\/\d+$/.test(location.pathname), { timeout: 8000 }).catch(() => {});
check('a confirmed customer can place the order', /^\/orders\/\d+$/.test(new URL(page.url()).pathname), page.url());

// ---------- confirming on another device: this tab notices when it's focused again ----------
const other = `otto${stamp}@example.com`;
const otto = await call('POST', '/auth/register', null, { name: 'Otto Other', email: other, password: 'correct-horse-battery' });
await page.evaluate((t) => localStorage.setItem('pacific.token', t), otto.token);
await go('/', 'home (otto)');
check('another new customer sees the reminder', (await text('.confirm-email')).includes(other));
await call('POST', '/auth/verify-email', null, { token: linkFor(other) }); // "on their phone"
await page.evaluate(() => window.dispatchEvent(new Event('focus')));
await sleep(800);
check('coming back to the tab after confirming elsewhere clears the reminder', (await page.$('.confirm-email')) === null);

// ---------- admin: the log hides links; confirming by hand ----------
const admin = (await call('POST', '/auth/admin/login', null, { identifier: 'e2eadmin', password: 'e2e-admin-password' })).token;
const log = await call('GET', '/admin/emails?size=50', admin);
const confirmation = log.items.find((e) => e.to === email && e.subject.includes('Confirm your email'));
check('the admin email log shows the confirmation email with its link hidden', confirmation && confirmation.body.includes('[confirmation link hidden]') && !confirmation.body.includes(firstLink), JSON.stringify(confirmation)?.slice(0, 200));
const stuck = `stuck${stamp}@example.com`;
await call('POST', '/auth/register', null, { name: 'Stu Stuck', email: stuck, password: 'correct-horse-battery' });
await page.evaluate((t) => localStorage.setItem('pacific.token', t), admin);
await go('/admin/emails', 'admin emails');
await clickText('.page summary', "Confirm a customer's email by hand");
await page.type('input[aria-label="Customer\'s email"]', stuck);
await clickText('.page button', 'Confirm email');
await sleep(800);
check('an admin can confirm a customer by hand', (await text('.page .notice.ok')).includes(stuck), await text('.page'));
const stuckLogin = await call('POST', '/auth/login', null, { identifier: stuck, password: 'correct-horse-battery' });
check('...and that customer now counts as confirmed', stuckLogin.user.emailVerified === true);
await shot('qa-verify-admin');

// ---------- phone width ----------
const phone = `phone${stamp}@example.com`;
const ph = await call('POST', '/auth/register', null, { name: 'Pho Ne', email: phone, password: 'correct-horse-battery' });
await page.evaluate((t) => localStorage.setItem('pacific.token', t), ph.token);
await page.setViewport({ width: 390, height: 844 });
await go('/', 'home (phone)');
const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
check('the reminder fits a phone screen', (await page.$('.confirm-email')) !== null && overflow <= 0, `overflows by ${overflow}px`);
await shot('qa-verify-phone');
await confirmEmail(phone);

console.log('\n=== problems seen in the browser ===');
const unique = [...new Set(problems)];
console.log(unique.length ? unique.join('\n') : 'none');
await browser.close();
const failed = results.filter((r) => !r).length;
console.log(`\n${results.length - failed}/${results.length} checks passed, ${unique.length} distinct browser problem(s)`);
process.exit(failed || unique.length ? 1 : 0);
