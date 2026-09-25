// Buyer–seller messages in a real browser: a buyer writes to a store from a product page, the seller sees the unread
// badge, reads and replies, the buyer sees the reply; a seller writes to the buyer of an order; nobody else can read
// the conversation; the notification email doesn't quote the message; and the pages fit a phone screen.
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
async function customer(name) {
  const email = `${name.split(' ')[0].toLowerCase()}${stamp}@example.com`;
  const reg = await call('POST', '/auth/register', null, { name, email, password: 'correct-horse-battery' });
  await confirmEmail(email);
  return { ...reg, email };
}

const admin = (await call('POST', '/auth/admin/login', null, { identifier: 'e2eadmin', password: 'e2e-admin-password' })).token;
const sam = await customer('Sam Seller');
const store = await call('POST', '/seller/apply', sam.token, { storeName: `Chat Shop ${stamp % 10000}`, description: 'We answer quickly.' });
await call('PATCH', `/admin/sellers/${store.id}/status`, admin, { status: 'APPROVED' });
const product = await call('POST', '/seller/products', sam.token, { name: 'Talking Teapot', price: '18.00', stock: 20, description: 'Whistles.' });
const bea = await customer('Bea Buyer');
const nosy = await customer('Nora Nosy');

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
async function as(token) {
  await page.goto(BASE + '/login', { waitUntil: 'domcontentloaded' });
  await page.evaluate((t) => localStorage.setItem('pacific.token', t), token);
}
async function shot(name) { await page.screenshot({ path: `${OUT}${name}.png`, fullPage: true }); }
const text = (sel) => page.$eval(sel, (e) => e.textContent).catch(() => '');
const bubbles = () => page.$$eval('.chat-msg', (ms) => ms.map((m) => ({ mine: m.classList.contains('mine'), text: m.querySelector('.chat-bubble').textContent })));

// ---------- a buyer writes from the product page ----------
await as(bea.token);
await go(`/products/${product.id}`, 'product page');
const link = await page.$('a.message-seller');
check('the product page offers "Message the seller"', !!link);
await link.click();
await page.waitForSelector('.composer textarea', { timeout: 8000 });
await sleep(400);
where = 'new message';
check('the new-message page says which product it is about', (await text('.page')).includes('About Talking Teapot'), await text('.page'));
await page.type('.composer textarea', 'Hello! Does the teapot come with a strainer?\nAnd is it dishwasher safe?');
await page.click('.composer .submit-btn');
await page.waitForFunction(() => /^\/messages\/\d+$/.test(location.pathname), { timeout: 8000 }).catch(() => {});
await sleep(400);
const convPath = new URL(page.url()).pathname;
check('sending opens the conversation', /^\/messages\/\d+$/.test(convPath), page.url());
let b = await bubbles();
check('the message shows as mine, line breaks kept', b.length === 1 && b[0].mine && b[0].text.includes('strainer?\nAnd'), JSON.stringify(b));
check('...marked as about the product', (await text('.chat-about')).includes('Talking Teapot'));
await shot('qa-messages-buyer');

// ---------- the seller sees it, reads it and replies ----------
await as(sam.token);
await go('/seller', 'seller central');
check('Seller Central shows an unread badge on Messages', (await text('.nav-bar a[href="/seller/messages"]')).includes('1'), await text('.nav-bar'));
await go('/seller/messages', 'seller inbox');
check('the seller inbox lists the buyer, unread, with a preview', (await text('.inbox-row.unread')).includes('Bea Buyer') && (await text('.inbox-row')).includes('strainer'), await text('.inbox'));
await shot('qa-messages-seller-inbox');
await page.click('.inbox-row');
await page.waitForSelector('.chat-msg', { timeout: 8000 });
await sleep(600);
where = 'seller thread';
b = await bubbles();
check('the seller sees the buyer\'s message', b.length === 1 && !b[0].mine, JSON.stringify(b));
check('reading clears the badge', !(await text('.nav-bar a[href="/seller/messages"]')).match(/\d/), await text('.nav-bar'));
await page.type('.composer textarea', 'Yes to both! It ships with a steel strainer.');
await page.keyboard.down('Control'); await page.keyboard.press('Enter'); await page.keyboard.up('Control');
await sleep(800);
b = await bubbles();
check('Ctrl+Enter sends the reply', b.length === 2 && b[1].mine && b[1].text.includes('steel strainer'), JSON.stringify(b));

// ---------- the buyer gets the reply ----------
await as(bea.token);
await go('/', 'home (buyer)');
check('the header shows the buyer an unread badge', (await text('a[href="/messages"]')).includes('1'), await text('.header-actions'));
await go(convPath, 'buyer thread');
b = await bubbles();
check('the buyer sees the seller\'s reply', b.length === 2 && !b[1].mine && b[1].text.includes('steel strainer'), JSON.stringify(b));
await go('/', 'home (buyer, read)');
check('...and the badge clears', !(await text('a[href="/messages"]')).match(/\d/), await text('a[href="/messages"]'));

// ---------- the seller writes to the buyer of an order ----------
await call('POST', '/cart/items', bea.token, { productId: product.id, quantity: 1 });
const placed = await call('POST', '/orders', bea.token, { name: 'Bea Buyer', line1: '1 High St', city: 'Uxbridge', postcode: 'UB8 1AA', country: 'UK' });
const orderId = placed.orders[0].id;
await as(sam.token);
await go('/seller/orders', 'seller orders');
await page.evaluate((id) => [...document.querySelectorAll('details summary')].find((s) => s.textContent.includes(`#${id}`))?.click(), orderId);
await sleep(300);
const msgBuyer = await page.$(`a[href="/seller/messages/new?order=${orderId}"]`);
check('a seller order offers "Message Bea Buyer"', !!msgBuyer && (await msgBuyer.evaluate((a) => a.textContent)).includes('Bea Buyer'));
await msgBuyer.click();
await page.waitForSelector('.composer textarea', { timeout: 8000 });
where = 'seller new message';
await page.type('.composer textarea', 'Your teapot ships tomorrow morning.');
await page.click('.composer .submit-btn');
await page.waitForFunction(() => /^\/seller\/messages\/\d+$/.test(location.pathname), { timeout: 8000 }).catch(() => {});
await sleep(400);
check('the seller\'s message joins the same conversation', new URL(page.url()).pathname === `/seller${convPath}`, page.url());
check('...marked as about the order', (await text('.chat-log')).includes(`order #${orderId}`), await text('.chat-log'));

// ---------- the seller's own product has no message link ----------
await go(`/products/${product.id}`, 'own product page');
check('sellers aren\'t offered to message their own store', (await page.$('a.message-seller')) === null);

// ---------- nobody else can read it ----------
await as(nosy.token);
expecting = [404];
await go(convPath, 'someone else\'s thread');
check('another customer can\'t open the conversation', (await text('.page')).includes('Conversation not found'), await text('.page'));
expecting = [];

// ---------- the email doesn't quote the message ----------
const log = await call('GET', '/admin/emails?size=50', admin);
const notice = log.items.find((e) => e.to === sam.email && e.subject.startsWith('New message from'));
check('the seller was emailed about the new message', !!notice, JSON.stringify(log.items.slice(0, 3)).slice(0, 200));
check('...without quoting it', notice && !notice.body.includes('strainer'), notice?.body);

// ---------- phone width ----------
await as(bea.token);
await page.setViewport({ width: 390, height: 844 });
for (const [path, label] of [['/messages', 'inbox (phone)'], [convPath, 'thread (phone)']]) {
  await go(path, label);
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  check(`the ${label} fits`, overflow <= 0, `overflows by ${overflow}px`);
}
await shot('qa-messages-phone');

console.log('\n=== problems seen in the browser ===');
const unique = [...new Set(problems)];
console.log(unique.length ? unique.join('\n') : 'none');
await browser.close();
const failed = results.filter((r) => !r).length;
console.log(`\n${results.length - failed}/${results.length} checks passed, ${unique.length} distinct browser problem(s)`);
process.exit(failed || unique.length ? 1 : 0);
