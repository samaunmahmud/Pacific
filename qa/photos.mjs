// Product photos in a real browser: an approved seller uploads a photo through the product form, it is resized and
// shown on the storefront; a bad file gets a clear message; photos can be removed or swapped for a link; and an
// admin can save a demo product with its drawing unchanged.
import puppeteer from 'puppeteer-core';
import { mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { crc32, deflateSync } from 'node:zlib';
import { confirmEmail } from './confirm.mjs';

const BASE = process.env.QA_BASE || 'http://localhost:5180';
const OUT = new URL('./shots/', import.meta.url).pathname;
const API = BASE + '/api';
const results = [];
const problems = [];
let where = 'start';
let expectingRejection = false;

const check = (name, ok, detail = '') => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + name + (ok ? '' : `  [${detail}]`)); };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
async function call(method, url, token, body) {
  const r = await fetch(API + url, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) }, body: body ? JSON.stringify(body) : undefined });
  const t = await r.text();
  return t ? JSON.parse(t) : null;
}

/** A w×h PNG, red on the left half and blue on the right, built by hand so the script needs no image library. */
function png(w, h) {
  const chunk = (type, data) => {
    const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
    const body = Buffer.concat([Buffer.from(type), data]);
    const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(body) >>> 0);
    return Buffer.concat([len, body, crc]);
  };
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 2;
  const row = Buffer.alloc(1 + w * 3);
  for (let x = 0; x < w; x++) row.set(x < w / 2 ? [220, 30, 40] : [30, 60, 220], 1 + x * 3);
  const raw = Buffer.concat(Array.from({ length: h }, () => row));
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr), chunk('IDAT', deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}

const dir = mkdtempSync(join(tmpdir(), 'qa-photos-'));
const bigPhoto = join(dir, 'lamp.png');
writeFileSync(bigPhoto, png(2400, 1600));
const fakePhoto = join(dir, 'notes.jpg');
writeFileSync(fakePhoto, '<html>this is not a photo</html>');

const browser = await puppeteer.launch({ executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: 'new', args: ['--hide-scrollbars'] });
const page = await browser.newPage();
await page.setViewport({ width: 1440, height: 900 });
page.on('pageerror', (e) => problems.push(`[${where}] uncaught: ${e.message}`));
let typingLink = false; // the preview follows the link as it's typed, so half-typed hosts ("https://exa") fail to resolve
page.on('console', (m) => { if (m.type() === 'error' && !(expectingRejection && m.text().includes('400')) && !(typingLink && m.text().includes('ERR_NAME_NOT_RESOLVED'))) problems.push(`[${where}] console.error: ${m.text().slice(0, 160)}`); });
page.on('response', (r) => {
  const u = r.url();
  if (u.startsWith(BASE) && r.status() >= 400 && !(expectingRejection && r.status() === 400) && !u.includes('/@vite')) problems.push(`[${where}] HTTP ${r.status()} ${r.request().method()} ${u.replace(BASE, '')}`);
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
const previewSrc = () => page.$eval('.photo-preview img', (i) => i.getAttribute('src')).catch(() => null);
async function waitForPreview(prefix) {
  for (let i = 0; i < 50; i++) { const s = await previewSrc(); if (s && s.startsWith(prefix)) return s; await sleep(200); }
  return previewSrc();
}

// ---------- an approved seller ----------
const reg = await call('POST', '/auth/register', null, { name: 'Pia Photos', email: `photos${Date.now()}@example.com`, password: 'correct-horse-battery' });
await confirmEmail(reg.user.email);
const store = await call('POST', '/seller/apply', reg.token, { storeName: 'Photo QA ' + Date.now(), description: 'Photographed things' });
const adminTok = (await call('POST', '/auth/admin/login', null, { identifier: 'e2eadmin', password: 'e2e-admin-password' })).token;
await call('PATCH', `/admin/sellers/${store.id}/status`, adminTok, { status: 'APPROVED' });
await page.goto(BASE + '/login', { waitUntil: 'domcontentloaded' });
await page.evaluate((t) => localStorage.setItem('pacific.token', t), reg.token);

// ---------- upload through the form ----------
await go('/seller/products/new', 'new product');
check('the product form has a photo field', (await text('.photo-field')).includes('Upload photo'), await text('.photo-field'));
await shot('qa-photo-empty');
const input = await page.$('.photo-field input[type=file]');
await input.uploadFile(bigPhoto);
const src = await waitForPreview('/api/images/');
check('the uploaded photo shows in the preview', /^\/api\/images\/[0-9a-f]{32}\.jpg$/.test(src || ''), src);
const size = await page.$eval('.photo-preview img', (i) => new Promise((ok) => (i.complete ? ok([i.naturalWidth, i.naturalHeight]) : (i.onload = () => ok([i.naturalWidth, i.naturalHeight])))));
check('a 2400×1600 photo is saved at 1600×1067', size[0] === 1600 && size[1] === 1067, size.join('×'));
check('the button now offers to replace the photo', (await text('.photo-field')).includes('Replace photo'));
await shot('qa-photo-uploaded');

await page.type('#pn', 'Photographed desk lamp');
await page.type('#pp', '24.00');
await page.$eval('#ps', (e) => { e.value = ''; });
await page.type('#ps', '4');
await clickText('.app-main button', 'Save product');
await sleep(1200);
const mine = await call('GET', '/seller/products?q=Photographed', reg.token);
const product = mine?.items?.find((p) => p.name === 'Photographed desk lamp');
check('the product is saved with the uploaded photo', product && product.imageUrl === src, JSON.stringify(product));

// ---------- shoppers see it ----------
await go(`/products/${product.id}`, 'product page');
const shown = await page.$$eval('.app-main img', (imgs, s) => imgs.filter((i) => i.getAttribute('src') === s).map((i) => i.naturalWidth), src);
check('the storefront product page shows the photo', shown.length > 0 && shown[0] > 0, JSON.stringify(shown));
await shot('qa-photo-pdp');
await go('/products?q=Photographed', 'catalogue');
const inGrid = await page.$$eval('img', (imgs, s) => imgs.some((i) => i.getAttribute('src') === s && i.naturalWidth > 0), src);
check('the catalogue card shows the photo', inGrid);

// ---------- a file that isn't a photo ----------
await go(`/seller/products/${product.id}`, 'edit product');
expectingRejection = true;
await (await page.$('.photo-field input[type=file]')).uploadFile(fakePhoto);
await sleep(1500);
expectingRejection = false;
const err = await text('.photo-field .notice.error');
check('a file that isn\'t a photo gets a clear message', /isn't a photo|couldn't be read|JPEG, PNG or GIF/.test(err), err);
check('the existing photo is kept after a bad upload', (await previewSrc()) === src, await previewSrc());
await shot('qa-photo-bad-file');

// ---------- remove, then use a link instead ----------
await clickText('.photo-field button', 'Remove');
await sleep(200);
check('removing the photo falls back to a drawing', (await previewSrc()) === null && (await text('.photo-field')).includes('No photo yet'));
await clickText('.photo-field button', 'Use a link instead');
await sleep(200);
typingLink = true;
await page.type('.photo-field input[type=url]', 'https://example.com/lamp.jpg');
await sleep(500);
typingLink = false;
check('typing a link keeps what was typed', (await page.$eval('.photo-field input[type=url]', (e) => e.value)) === 'https://example.com/lamp.jpg');
await clickText('.app-main button', 'Save product');
await sleep(1200);
const after = await call('GET', `/seller/products/${product.id}`, reg.token);
check('the product now uses the linked photo', after.imageUrl === 'https://example.com/lamp.jpg', after.imageUrl);

// ---------- phone-width layout ----------
await page.setViewport({ width: 390, height: 844 });
await go('/seller/products/new', 'new product (phone)');
const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
check('the photo field fits a phone screen', overflow <= 0, `overflows by ${overflow}px`);
await shot('qa-photo-phone');
await page.setViewport({ width: 1440, height: 900 });

// ---------- admin edits a demo product without touching its drawing ----------
await page.evaluate((t) => localStorage.setItem('pacific.token', t), adminTok);
// Most reviewed first: demo products, even after test runs have added plenty of newer ones.
const house = await call('GET', '/admin/products?size=50&sort=popular', adminTok);
const demo = house?.items?.find((p) => p.imageUrl?.startsWith('demo:') && p.catalogId === p.id); // a product page, not another store's offer
if (demo) {
  await go(`/admin/products/${demo.id}`, 'edit demo product');
  await page.$eval('#pn', (e) => { e.value = ''; });
  await page.type('#pn', demo.name + ' (edited)');
  await clickText('.app-main button', 'Save product');
  await sleep(1200);
  const saved = await call('GET', `/admin/products/${demo.id}`, adminTok);
  check('a demo product saves with its drawing unchanged', saved.name.endsWith('(edited)') && saved.imageUrl === demo.imageUrl, JSON.stringify(saved).slice(0, 200));
} else {
  check('a demo product exists to edit (run with DEMO_DATA=true)', false, 'no demo: products');
}

console.log('\n=== problems seen in the browser ===');
const unique = [...new Set(problems)];
console.log(unique.length ? unique.join('\n') : 'none');
await browser.close();
const failed = results.filter((r) => !r).length;
console.log(`\n${results.length - failed}/${results.length} checks passed, ${unique.length} distinct browser problem(s)`);
process.exit(failed || unique.length ? 1 : 0);
