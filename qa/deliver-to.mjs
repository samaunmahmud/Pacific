// "Deliver to" in a real browser: a signed-out shopper enters a postcode (a wrong one is refused) and uses their
// device's location; a customer sees their default address, picks another, and checkout starts with it; the buy box
// shows it; signing out doesn't show it; phone layouts. Start the backend with LOCATION_API_BASE pointing at
// postcodes_stub.py (see README).
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
const email = `casey${stamp}@example.com`;
const casey = await call('POST', '/auth/register', null, { name: 'Casey Jones', email, password: 'correct-horse-battery' });
await confirmEmail(email);
await call('POST', '/me/addresses', casey.token, { name: 'Casey Jones', line1: '1 High Street', city: 'Uxbridge', postcode: 'UB8 3PH', country: 'United Kingdom', makeDefault: true });
const work = await call('POST', '/me/addresses', casey.token, { name: 'Casey at work', line1: '5 Market Street', city: 'Manchester', postcode: 'M1 1AE', country: 'United Kingdom', makeDefault: false });
const product = (await call('GET', '/products?size=1')).items[0];

const browser = await puppeteer.launch({ executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: 'new', args: ['--hide-scrollbars'] });
await browser.defaultBrowserContext().overridePermissions(BASE, ['geolocation']);
const tab = await browser.newPage();
await tab.setViewport({ width: 1440, height: 900 });
await tab.setGeolocation({ latitude: 51.5080, longitude: -0.1281 });
tab.on('pageerror', (e) => problems.push(`[${where}] uncaught: ${e.message}`));
tab.on('console', (m) => { if (m.type() === 'error') problems.push(`[${where}] console.error: ${m.text().slice(0, 160)}`); });
tab.on('response', (r) => { const u = r.url(); if (u.startsWith(BASE) && r.status() >= 400 && !u.includes('/@vite')) problems.push(`[${where}] HTTP ${r.status()} ${r.request().method()} ${u.replace(BASE, '')}`); });
async function go(path, label = path) { where = label; await tab.goto(BASE + path, { waitUntil: 'networkidle0' }); await sleep(400); }
async function shot(name) { await tab.screenshot({ path: `${OUT}${name}.png` }); }
const text = (sel) => tab.$eval(sel, (e) => e.textContent).catch(() => '');
const header = () => text('button.deliver-to');
async function openDialog(sel = 'button.deliver-to') {
  await tab.click(sel);
  await tab.waitForSelector('dialog.deliver-dialog[open]', { timeout: 5000 }).catch(() => {});
  await sleep(300);
}
async function typePostcode(value) {
  await tab.click('.deliver-dialog input', { clickCount: 3 });
  await tab.keyboard.press('Backspace');
  await tab.type('.deliver-dialog input', value);
  await tab.click('.deliver-dialog form .submit-btn');
  await sleep(900);
}

// ---------- signed out ----------
await tab.goto(BASE + '/', { waitUntil: 'domcontentloaded' });
await tab.evaluate(() => { localStorage.clear(); });
await go('/', 'home (guest)');
check('a new visitor is asked for a location', (await header()).includes('Update location'), await header());
await openDialog();
check('the dialog offers signing in for saved addresses', (await text('.deliver-dialog')).includes('Sign in to see your addresses'), await text('.deliver-dialog'));
await typePostcode('hello');
check('a postcode that isn\'t one is refused', (await text('.deliver-dialog .notice.error')).includes('Enter a UK postcode'), await text('.deliver-dialog .notice.error'));
await typePostcode('ZZ99 1ZZ');
check('an unknown postcode is refused', (await text('.deliver-dialog .notice.error')).includes('couldn\'t find ZZ99 1ZZ'), await text('.deliver-dialog .notice.error'));
// The two refusals above, as the browser reports them.
problems.splice(0, problems.length, ...problems.filter((p) => !/HTTP (400|404) GET \/api\/location/.test(p)
  && !/status of (400|404)/.test(p)));
await shot('qa-deliver-dialog');
await typePostcode('ub83ph');
check('a real postcode is named in the top bar', (await header()).includes('Hillingdon UB8 3PH') && !(await tab.$('dialog.deliver-dialog[open]')), await header());
await go('/products', 'catalog (guest)');
check('it stays after reloading', (await header()).includes('Hillingdon UB8 3PH'), await header());
await openDialog();
await tab.click('.deliver-device');
await sleep(1500);
check('"Use my current location" finds the nearest postcode', (await header()).includes('Westminster WC2N 5DU'), await header());

// ---------- signed in ----------
await tab.evaluate((t) => localStorage.setItem('pacific.token', t), casey.token);
await go('/', 'home (Casey)');
check('a customer sees their default address, not the guest postcode', (await header()).includes('Deliver to Casey') && (await header()).includes('Uxbridge UB8 3PH'), await header());
await openDialog();
const listed = await tab.$$eval('.deliver-address', (els) => els.map((e) => e.textContent));
check('the dialog lists their saved addresses', listed.length === 2 && listed[0].includes('Default address'), JSON.stringify(listed));
await shot('qa-deliver-addresses');
await tab.evaluate(() => [...document.querySelectorAll('.deliver-address')][1].click());
await sleep(500);
check('choosing another address updates the top bar', (await header()).includes('Manchester M1 1AE'), await header());
await go(`/products/${product.id}`, 'product page');
check('the buy box says where it will go', (await text('.bb-deliver-to')).includes('Manchester M1 1AE'), await text('.bb-deliver-to'));
await call('POST', '/cart/items', casey.token, { productId: product.boxProductId, quantity: 1 });
await go('/checkout', 'checkout');
const checkedAddress = await tab.evaluate(() => { const r = [...document.querySelectorAll('input[name="address"]')].find((i) => i.checked); return r ? r.closest('label').textContent : ''; });
check('checkout starts with the chosen address', checkedAddress.includes('5 Market Street'), checkedAddress);
await call('DELETE', `/me/addresses/${work.id}`, casey.token);
await go('/', 'home after deleting the chosen address');
check('a deleted address falls back to the default', (await header()).includes('Uxbridge UB8 3PH'), await header());

// ---------- signed out again ----------
await tab.evaluate(() => localStorage.removeItem('pacific.token'));
await go('/', 'home (signed out)');
check('signing out doesn\'t show the customer\'s address', !(await header()).includes('Casey') && !(await header()).includes('Uxbridge'), await header());

// ---------- phone ----------
await tab.setViewport({ width: 390, height: 844 });
await go('/', 'home (phone)');
check('phones get a "Deliver to" strip', await tab.$eval('.deliver-strip', (e) => getComputedStyle(e).display !== 'none').catch(() => false));
await openDialog('.deliver-strip');
await typePostcode('SW1A 1AA');
check('the strip opens the same dialog', (await text('.deliver-strip')).includes('Westminster SW1A 1AA'), await text('.deliver-strip'));
const overflow = await tab.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
check('the page fits a phone', overflow <= 0, `overflows by ${overflow}px`);
await openDialog('.deliver-strip');
const fits = await tab.$eval('dialog.deliver-dialog', (d) => d.getBoundingClientRect().right <= window.innerWidth).catch(() => false);
check('the dialog fits a phone', fits);
await shot('qa-deliver-phone');

console.log('\n=== problems seen in the browser ===');
const unique = [...new Set(problems)];
console.log(unique.length ? unique.join('\n') : 'none');
await browser.close();
const failed = results.filter((r) => !r).length;
console.log(`\n${results.length - failed}/${results.length} checks passed, ${unique.length} distinct browser problem(s)`);
process.exit(failed || unique.length ? 1 : 0);
