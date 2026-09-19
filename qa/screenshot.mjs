// Usage: node shoot.mjs <name> <path> <width> <height> [mode]
// mode: empty  -> a brand-new customer with an empty cart
//       full   -> the demo shopper with items from three sellers in the cart (default)
import puppeteer from 'puppeteer-core';

const [name, path, width = '1440', height = '900', mode = 'full'] = process.argv.slice(2);
const API = 'http://localhost:5180/api';
const OUT = new URL('./shots/', import.meta.url).pathname;

async function call(method, url, token, body) {
  const res = await fetch(API + url, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

let token;
if (mode === 'empty') {
  token = (await call('POST', '/auth/register', null, { name: 'Fresh Shopper', email: `fresh${Date.now()}@example.com`, password: 'correct-horse-battery' })).token;
} else {
  token = (await call('POST', '/auth/login', null, { identifier: 'demo.shopper@example.com', password: 'Demo-Pacific-123' })).token;
  // start from a known cart: clear it, then add a few products from different sellers (and one with low stock)
  const cart = await call('GET', '/cart', token);
  for (const i of cart.items) await call('DELETE', `/cart/items/${i.productId}`, token);
  const list = (await call('GET', '/products?size=48&sort=popular', null)).items;
  const bySeller = new Map();
  for (const p of list) if (p.stock > 5 && !bySeller.has(p.sellerName)) bySeller.set(p.sellerName, p);
  const picks = [...bySeller.values()].slice(0, 3);
  const low = (await call('GET', '/products?size=48', null)).items.find((p) => p.stock > 0 && p.stock <= 5);
  for (const [n, p] of picks.entries()) await call('POST', '/cart/items', token, { productId: p.id, quantity: n === 0 ? 2 : 1 });
  if (low) await call('POST', '/cart/items', token, { productId: low.id, quantity: 1 });
}

const browser = await puppeteer.launch({
  executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
  headless: 'new',
  args: ['--hide-scrollbars'],
});
const page = await browser.newPage();
await page.setViewport({ width: Number(width), height: Number(height) });
await page.goto('http://localhost:5180/login', { waitUntil: 'domcontentloaded' });
await page.evaluate((t) => localStorage.setItem('pacific.token', t), token);
await page.goto('http://localhost:5180' + path, { waitUntil: 'networkidle0' });
await new Promise((r) => setTimeout(r, 600));
await page.screenshot({ path: `${OUT}${name}.png`, fullPage: true });
await browser.close();
console.log('saved', name);
