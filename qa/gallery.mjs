// Product galleries in a real browser: a seller adds several photos at once, reorders them, removes one and picks a
// new main photo; shoppers then flick through them on the product page with clicks and arrow keys. Also checks the
// eight-photo limit, that a one-photo product shows no thumbnails, and the phone layout.
import puppeteer from 'puppeteer-core';
import { mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { crc32, deflateSync } from 'node:zlib';

const BASE = process.env.QA_BASE || 'http://localhost:5180';
const OUT = new URL('./shots/', import.meta.url).pathname;
const API = BASE + '/api';
const results = [];
const problems = [];
let where = 'start';

const check = (name, ok, detail = '') => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + name + (ok ? '' : `  [${detail}]`)); };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
async function call(method, url, token, body) {
  const r = await fetch(API + url, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) }, body: body ? JSON.stringify(body) : undefined });
  const t = await r.text();
  return t ? JSON.parse(t) : null;
}

/** A plain w×h PNG in one colour, built by hand so the script needs no image library. */
function png(w, h, [r, g, b]) {
  const chunk = (type, data) => {
    const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
    const body = Buffer.concat([Buffer.from(type), data]);
    const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(body) >>> 0);
    return Buffer.concat([len, body, crc]);
  };
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 2;
  const row = Buffer.alloc(1 + w * 3);
  for (let x = 0; x < w; x++) row.set([r, g, b], 1 + x * 3);
  const raw = Buffer.concat(Array.from({ length: h }, () => row));
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr), chunk('IDAT', deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}

const dir = mkdtempSync(join(tmpdir(), 'qa-gallery-'));
const COLOURS = [[200, 40, 40], [40, 160, 60], [40, 70, 200], [220, 180, 30], [150, 50, 170], [30, 170, 170], [240, 120, 20], [90, 90, 90]];
const files = COLOURS.map((c, i) => { const f = join(dir, `photo-${i + 1}.png`); writeFileSync(f, png(400, 300, c)); return f; });

const browser = await puppeteer.launch({ executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: 'new', args: ['--hide-scrollbars'] });
const page = await browser.newPage();
await page.setViewport({ width: 1440, height: 900 });
page.on('pageerror', (e) => problems.push(`[${where}] uncaught: ${e.message}`));
page.on('console', (m) => { if (m.type() === 'error') problems.push(`[${where}] console.error: ${m.text().slice(0, 160)}`); });
page.on('response', (r) => {
  const u = r.url();
  if (u.startsWith(BASE) && r.status() >= 400 && !u.includes('/@vite')) problems.push(`[${where}] HTTP ${r.status()} ${r.request().method()} ${u.replace(BASE, '')}`);
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
const extras = () => page.$$eval('.more-photo-img img', (imgs) => imgs.map((i) => i.getAttribute('src')));
const mainSrc = () => page.$eval('.photo-preview img', (i) => i.getAttribute('src')).catch(() => null);
async function waitFor(fn, ok, tries = 60) {
  for (let i = 0; i < tries; i++) { const v = await fn(); if (ok(v)) return v; await sleep(200); }
  return fn();
}
const pdpSrc = () => page.$eval('.pdp-image .image-container img', (i) => i.getAttribute('src')).catch(() => null);

// ---------- an approved seller ----------
const reg = await call('POST', '/auth/register', null, { name: 'Gia Gallery', email: `gallery${Date.now()}@example.com`, password: 'correct-horse-battery' });
const store = await call('POST', '/seller/apply', reg.token, { storeName: 'Gallery QA ' + Date.now(), description: 'Photographed from every side' });
const adminTok = (await call('POST', '/auth/admin/login', null, { identifier: 'e2eadmin', password: 'e2e-admin-password' })).token;
await call('PATCH', `/admin/sellers/${store.id}/status`, adminTok, { status: 'APPROVED' });
await page.goto(BASE + '/login', { waitUntil: 'domcontentloaded' });
await page.evaluate((t) => localStorage.setItem('pacific.token', t), reg.token);

// ---------- a main photo and three more, chosen at once ----------
await go('/seller/products/new', 'new product');
check('the form offers more photos', (await text('.more-photos')).includes('Add more photos'), await text('.more-photos'));
await (await page.$('.photo-field input[type=file]')).uploadFile(files[0]);
const main = await waitFor(mainSrc, (s) => s?.startsWith('/api/images/'));
await (await page.$('.more-photos input[type=file]')).uploadFile(files[1], files[2], files[3]);
const three = await waitFor(extras, (l) => l.length === 3);
check('three photos chosen together are all added', three.length === 3 && three.every((s) => s.startsWith('/api/images/')), JSON.stringify(three));
check('saving waits while photos upload, then is allowed', await waitFor(() => page.$eval('.submit-btn', (b) => !b.disabled), (v) => v));
await shot('qa-gallery-form');

// ---------- reorder, remove, make main ----------
await page.click('[aria-label="Move photo 4 earlier"]');
await sleep(150);
check('a photo can be moved earlier', JSON.stringify(await extras()) === JSON.stringify([three[0], three[2], three[1]]), JSON.stringify(await extras()));
await page.click('[aria-label="Remove photo 2"]');
await sleep(150);
check('a photo can be removed', JSON.stringify(await extras()) === JSON.stringify([three[2], three[1]]), JSON.stringify(await extras()));
await clickText('.more-photo button', 'Make main photo');
await sleep(150);
check('"Make main photo" swaps it with the main photo', (await mainSrc()) === three[2] && JSON.stringify(await extras()) === JSON.stringify([main, three[1]]),
  `${await mainSrc()} ${JSON.stringify(await extras())}`);

await page.type('#pn', 'Gallery teapot');
await page.type('#pp', '31.00');
await page.$eval('#ps', (e) => { e.value = ''; });
await page.type('#ps', '5');
await clickText('.app-main button', 'Save product');
await sleep(1200);
const product = (await call('GET', '/seller/products?q=Gallery%20teapot', reg.token))?.items?.[0];
check('the product is saved with its photos in order', product?.imageUrl === three[2] && JSON.stringify(product?.moreImages) === JSON.stringify([main, three[1]]), JSON.stringify(product));

// ---------- shoppers flick through them ----------
await go(`/products/${product.id}`, 'product page');
const thumbs = await page.$$('.pdp-thumb');
check('the product page shows a thumbnail per photo', thumbs.length === 3, `${thumbs.length} thumbnails`);
check('the main photo shows first', (await pdpSrc()) === three[2], await pdpSrc());
await thumbs[1].click();
await sleep(200);
check('clicking a thumbnail shows that photo', (await pdpSrc()) === main, await pdpSrc());
await page.keyboard.press('ArrowRight');
await sleep(200);
check('the right arrow moves to the next photo', (await pdpSrc()) === three[1], await pdpSrc());
await page.keyboard.press('ArrowRight');
await sleep(200);
check('and wraps round to the first', (await pdpSrc()) === three[2], await pdpSrc());
const alt = await page.$eval('.pdp-image .image-container img', (i) => i.alt);
check('the big photo says which photo it is', alt === 'Gallery teapot, photo 1 of 3', alt);
await shot('qa-gallery-pdp');

// ---------- a one-photo product shows no thumbnails ----------
const single = await call('POST', '/seller/products', reg.token, { name: 'Single-photo mug', price: '5.00', stock: 2, imageUrl: main });
await go(`/products/${single.id}`, 'single-photo product');
check('a product with one photo shows no thumbnails', (await page.$$('.pdp-thumb')).length === 0 && (await pdpSrc()) === main);

// ---------- the eight-photo limit ----------
await go('/seller/products/new', 'new product (limit)');
await (await page.$('.more-photos input[type=file]')).uploadFile(...files);
await waitFor(extras, (l) => l.length === 7);
check('only seven more photos are added', (await extras()).length === 7, `${(await extras()).length}`);
const limitText = await text('.more-photos');
check('the seller is told one was left out and the form is full', limitText.includes('1 was left out') && limitText.includes('most photos a product can have'), limitText);

// ---------- phone-width layout ----------
await page.setViewport({ width: 390, height: 844 });
await go(`/products/${product.id}`, 'product page (phone)');
let overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
check('the gallery fits a phone screen', overflow <= 0, `overflows by ${overflow}px`);
await shot('qa-gallery-phone');
await go(`/seller/products/${product.id}`, 'edit product (phone)');
overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
check('the more-photos field fits a phone screen', overflow <= 0, `overflows by ${overflow}px`);

console.log('\n=== problems seen in the browser ===');
const unique = [...new Set(problems)];
console.log(unique.length ? unique.join('\n') : 'none');
await browser.close();
const failed = results.filter((r) => !r).length;
console.log(`\n${results.length - failed}/${results.length} checks passed, ${unique.length} distinct browser problem(s)`);
process.exit(failed || unique.length ? 1 : 0);
