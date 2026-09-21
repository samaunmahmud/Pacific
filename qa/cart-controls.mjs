import puppeteer from 'puppeteer-core';
const API = 'http://localhost:5180/api';
const call = async (m, u, t, b) => { const r = await fetch(API + u, { method: m, headers: { 'Content-Type': 'application/json', ...(t ? { Authorization: 'Bearer ' + t } : {}) }, body: b ? JSON.stringify(b) : undefined }); const x = await r.text(); return x ? JSON.parse(x) : null; };
const token = (await call('POST', '/auth/login', null, { identifier: 'demo.shopper@example.com', password: 'Demo-Pacific-123' })).token;
// start from a known cart, whatever the shopper had before: two of a product from each of three different sellers
for (const line of (await call('GET', '/cart', token)).items) await call('DELETE', `/cart/items/${line.productId}`, token);
const bySeller = new Map();
for (let pageNo = 0; pageNo < 6 && bySeller.size < 3; pageNo++) {
  for (const p of (await call('GET', `/products?size=50&page=${pageNo}`, token)).items) {
    if (p.stock >= 5 && p.sellerSlug && !bySeller.has(p.sellerSlug) && bySeller.size < 3) bySeller.set(p.sellerSlug, p);
  }
}
for (const p of bySeller.values()) await call('POST', '/cart/items', token, { productId: p.id, quantity: 2 });
const results = [];
const check = (n, ok, d = '') => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + n + (ok ? '' : `  [${d}]`)); };

const browser = await puppeteer.launch({ executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: 'new' });
const page = await browser.newPage();
await page.setViewport({ width: 1440, height: 900 });
page.on('dialog', (d) => d.accept());
await page.goto('http://localhost:5180/login', { waitUntil: 'domcontentloaded' });
await page.evaluate((t) => localStorage.setItem('pacific.token', t), token);
await page.goto('http://localhost:5180/cart', { waitUntil: 'networkidle0' });

const serverCart = () => call('GET', '/cart', token);
const before = await serverCart();
check('cart has items from 3 sellers', before.shipments.length === 3, before.shipments.length);

// change the quantity of the first item with the dropdown
const first = before.items[0];
await page.select('.cart-item select', String(first.quantity + 1));
await new Promise((r) => setTimeout(r, 1200));
let now = await serverCart();
check('quantity dropdown updates the server', now.items.find((i) => i.productId === first.productId).quantity === first.quantity + 1);
check('summary shows the new item count', (await page.$eval('.cart-sub-line', (e) => e.textContent)).includes(`(${now.itemCount} item`));

// Save for later: leaves the cart, lands in the wish list
// (the page groups items by seller, so read the product id from the row we click rather than guessing the order)
const idOfRow = async (n) => Number((await page.$$eval('.cart-item .cart-item-title', (els) => els.map((e) => e.getAttribute('href'))))[n].split('/').pop());
const second = { productId: await idOfRow(1) };
await (await (await page.$$('.cart-item'))[1].$$('.cart-link-btn'))[1].click();
await new Promise((r) => setTimeout(r, 1200));
now = await serverCart();
const wish = await call('GET', '/wishlist/ids', token);
check('Save for later removes it from the cart', !now.items.some((i) => i.productId === second.productId));
check('...and adds it to the wish list', wish.includes(second.productId), JSON.stringify(wish));

// Delete
const third = { productId: await idOfRow(1) };
await (await (await page.$$('.cart-item'))[1].$$('.cart-link-btn'))[0].click();
await new Promise((r) => setTimeout(r, 1200));
now = await serverCart();
check('Delete removes the line', !now.items.some((i) => i.productId === third.productId));

// checkout button goes to checkout
await page.click('.cart-summary .cart-btn');
await page.waitForFunction(() => location.pathname === '/checkout', { timeout: 5000 }).catch(() => {});
check('Proceed to checkout opens /checkout', new URL(page.url()).pathname === '/checkout', page.url());

// clean up so the next screenshot run starts fresh
await call('DELETE', `/wishlist/${second.productId}`, token);
await browser.close();
process.exit(results.every(Boolean) ? 0 : 1);
