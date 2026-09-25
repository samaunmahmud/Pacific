// New customers must confirm their email before ordering, selling or asking questions. Scripts that register
// customers confirm them the way support would, through the admin endpoint; verification.mjs tests the real link.
const API = (process.env.QA_BASE || 'http://localhost:5180') + '/api';
let adminToken;

async function post(url, token, body) {
  const r = await fetch(API + url, { method: 'POST', headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) }, body: JSON.stringify(body) });
  if (!r.ok) throw new Error(`POST ${url} -> ${r.status} ${(await r.text()).slice(0, 200)}`);
  return r.json();
}

export async function confirmEmail(email) {
  adminToken ??= (await post('/auth/admin/login', null, { identifier: 'e2eadmin', password: 'e2e-admin-password' })).token;
  return post('/admin/customers/verify-email', adminToken, { email });
}
