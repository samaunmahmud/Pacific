import { useState, type FormEvent, type ReactNode } from 'react';
import { api, ApiError } from '../api/client';
import type { Page, Product, StorePromotions } from '../api/types';
import { dateTime, money } from '../ui/format';
import { useToast } from '../ui/Toast';
import { useAsync } from '../ui/useAsync';

/**
 * A store's promotions: Lightning Deals, coupons and promo codes. Sellers manage their own (/seller/promotions);
 * admins manage Pacific's own products (/admin/promotions). Each store pays for its own promotions.
 */
export function PromotionsManager({ base }: { base: 'seller' | 'admin' }) {
  const toast = useToast();
  const list = useAsync(() => api<StorePromotions>(`/${base}/promotions`), [base]);
  const listings = useAsync(async () => {
    const page = await api<Page<Product>>(`/${base}/products`, { query: { size: 48 } });
    // admins run promotions on Pacific's own products only
    return page.items.filter((p) => p.active && (base === 'seller' || p.sellerSlug === null));
  }, [base]);

  async function act(path: string, done: string) {
    try {
      await api(`/${base}/promotions/${path}`, { method: 'POST' });
      toast.show(done);
      list.reload();
    } catch (err) {
      toast.show(err instanceof Error ? err.message : 'Could not do that.', 'error');
    }
  }

  const products = listings.data ?? [];
  const data = list.data;
  return (
    <div className="page">
      <div>
        <h1 className="page-title">Promotions</h1>
        <p className="page-subtitle">
          {base === 'admin' ? "Deals, coupons and codes on Pacific's own products." : 'Deals, coupons and codes on your listings. Your store pays for the discount: orders are charged at the lower price.'}
        </p>
      </div>
      {list.error && <div className="notice error">{list.error}</div>}

      <PromoSection title="Lightning Deals" hint="A deal price for 1–12 hours on a set number of units, with a countdown on the product page.">
        <DealForm base={base} products={products} onDone={list.reload} />
        {data && data.deals.length > 0 && (
          <div className="table-wrap"><table className="data">
            <thead><tr><th>Listing</th><th>Deal price</th><th>Claimed</th><th>Runs</th><th>Status</th><th /></tr></thead>
            <tbody>
              {data.deals.map((d) => (
                <tr key={d.id}>
                  <td>{d.productName}</td>
                  <td>{money(d.price)} <s className="was">{money(d.regularPrice)}</s></td>
                  <td>{d.claimed} / {d.quantity}</td>
                  <td>{dateTime(d.startsAt)} – {dateTime(d.endsAt)}</td>
                  <td><span className={`status-pill promo-${d.status}`}>{d.status.replace('_', ' ').toLowerCase()}</span></td>
                  <td>{(d.status === 'LIVE' || d.status === 'SCHEDULED') && <button className="cart-link-btn" onClick={() => void act(`deals/${d.id}/end`, 'Deal ended')}>End now</button>}</td>
                </tr>
              ))}
            </tbody>
          </table></div>
        )}
      </PromoSection>

      <PromoSection title="Coupons" hint={'"Save 10% with coupon" on a listing. Shoppers clip it; each customer can use it once, up to your budget.'}>
        <CouponForm base={base} products={products} onDone={list.reload} />
        {data && data.coupons.length > 0 && (
          <div className="table-wrap"><table className="data">
            <thead><tr><th>Listing</th><th>Off</th><th>Used</th><th>Ends</th><th>Status</th><th /></tr></thead>
            <tbody>
              {data.coupons.map((c) => (
                <tr key={c.id}>
                  <td>{c.productName}</td><td>{c.percentOff}%</td><td>{c.used} / {c.budget}</td><td>{dateTime(c.endsAt)}</td>
                  <td><span className={`status-pill promo-${c.live ? 'LIVE' : 'ENDED'}`}>{c.live ? 'live' : 'ended'}</span></td>
                  <td>{c.live && <button className="cart-link-btn" onClick={() => void act(`coupons/${c.id}/stop`, 'Coupon stopped')}>Stop</button>}</td>
                </tr>
              ))}
            </tbody>
          </table></div>
        )}
      </PromoSection>

      <PromoSection title="Promo codes" hint="A code shoppers type at checkout for a percentage off your items. Once per customer.">
        <CodeForm base={base} onDone={list.reload} />
        {data && data.codes.length > 0 && (
          <div className="table-wrap"><table className="data">
            <thead><tr><th>Code</th><th>Off</th><th>Min spend</th><th>Used</th><th>Ends</th><th>Status</th><th /></tr></thead>
            <tbody>
              {data.codes.map((c) => (
                <tr key={c.id}>
                  <td><b>{c.code}</b></td><td>{c.percentOff}%</td><td>{c.minSpend > 0 ? money(c.minSpend) : '—'}</td>
                  <td>{c.used}{c.maxUses != null ? ` / ${c.maxUses}` : ''}</td><td>{dateTime(c.endsAt)}</td>
                  <td><span className={`status-pill promo-${c.live ? 'LIVE' : 'ENDED'}`}>{c.live ? 'live' : 'ended'}</span></td>
                  <td>{c.live && <button className="cart-link-btn" onClick={() => void act(`codes/${c.id}/stop`, 'Code stopped')}>Stop</button>}</td>
                </tr>
              ))}
            </tbody>
          </table></div>
        )}
      </PromoSection>
    </div>
  );
}

function PromoSection({ title, hint, children }: { title: string; hint: string; children: ReactNode }) {
  return (
    <section className="square-review-box static stack promo-section">
      <div><h2 style={{ margin: 0 }}>{title}</h2><p className="muted" style={{ margin: '4px 0 0' }}>{hint}</p></div>
      {children}
    </section>
  );
}

/** A small create form: posts the fields, then resets and refreshes the list. */
function useCreate(base: string, path: string, onDone: () => void, done: string) {
  const toast = useToast();
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  async function create(body: unknown) {
    setBusy(true);
    setError('');
    try {
      await api(`/${base}/promotions/${path}`, { method: 'POST', body });
      toast.show(done);
      onDone();
      return true;
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create it.');
      return false;
    } finally {
      setBusy(false);
    }
  }
  return { create, error, busy };
}

function ListingSelect({ id, products, value, onChange }: { id: string; products: Product[]; value: string; onChange: (v: string) => void }) {
  return (
    <select id={id} className="rounded-input" value={value} onChange={(e) => onChange(e.target.value)} required>
      <option value="">Choose a listing…</option>
      {products.map((p) => <option key={p.id} value={p.id}>{p.name} ({money(p.price)}, {p.stock} in stock)</option>)}
    </select>
  );
}

function DealForm({ base, products, onDone }: { base: string; products: Product[]; onDone: () => void }) {
  const [productId, setProductId] = useState('');
  const [price, setPrice] = useState('');
  const [quantity, setQuantity] = useState('5');
  const [hours, setHours] = useState('6');
  const { create, error, busy } = useCreate(base, 'deals', onDone, 'Lightning Deal created');
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (await create({ productId: Number(productId), price: Number(price), quantity: Number(quantity), hours: Number(hours) })) {
      setProductId(''); setPrice('');
    }
  }
  return (
    <form className="promo-form" onSubmit={submit} aria-label="New Lightning Deal">
      <label htmlFor="dl-p">Listing<ListingSelect id="dl-p" products={products} value={productId} onChange={setProductId} /></label>
      <label htmlFor="dl-price">Deal price (£)<input id="dl-price" className="rounded-input" inputMode="decimal" value={price} onChange={(e) => setPrice(e.target.value)} required /></label>
      <label htmlFor="dl-q">Units<input id="dl-q" className="rounded-input" type="number" min={1} value={quantity} onChange={(e) => setQuantity(e.target.value)} required /></label>
      <label htmlFor="dl-h">Hours<input id="dl-h" className="rounded-input" type="number" min={1} max={12} value={hours} onChange={(e) => setHours(e.target.value)} required /></label>
      <button className="submit-btn" disabled={busy}>{busy ? 'Starting…' : 'Start deal now'}</button>
      {error && <div className="notice error promo-error" role="alert">{error}</div>}
    </form>
  );
}

function CouponForm({ base, products, onDone }: { base: string; products: Product[]; onDone: () => void }) {
  const [productId, setProductId] = useState('');
  const [percent, setPercent] = useState('10');
  const [budget, setBudget] = useState('100');
  const [days, setDays] = useState('14');
  const { create, error, busy } = useCreate(base, 'coupons', onDone, 'Coupon created');
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (await create({ productId: Number(productId), percentOff: Number(percent), budget: Number(budget), days: Number(days) })) setProductId('');
  }
  return (
    <form className="promo-form" onSubmit={submit} aria-label="New coupon">
      <label htmlFor="cp-p">Listing<ListingSelect id="cp-p" products={products} value={productId} onChange={setProductId} /></label>
      <label htmlFor="cp-pc">% off<input id="cp-pc" className="rounded-input" type="number" min={5} max={50} value={percent} onChange={(e) => setPercent(e.target.value)} required /></label>
      <label htmlFor="cp-b">Customers<input id="cp-b" className="rounded-input" type="number" min={1} value={budget} onChange={(e) => setBudget(e.target.value)} required /></label>
      <label htmlFor="cp-d">Days<input id="cp-d" className="rounded-input" type="number" min={1} max={90} value={days} onChange={(e) => setDays(e.target.value)} required /></label>
      <button className="submit-btn" disabled={busy}>{busy ? 'Creating…' : 'Create coupon'}</button>
      {error && <div className="notice error promo-error" role="alert">{error}</div>}
    </form>
  );
}

function CodeForm({ base, onDone }: { base: string; onDone: () => void }) {
  const [code, setCode] = useState('');
  const [percent, setPercent] = useState('10');
  const [minSpend, setMinSpend] = useState('');
  const [maxUses, setMaxUses] = useState('');
  const [days, setDays] = useState('30');
  const { create, error, busy } = useCreate(base, 'codes', onDone, 'Promo code created');
  async function submit(e: FormEvent) {
    e.preventDefault();
    const ok = await create({ code: code.trim(), percentOff: Number(percent), minSpend: minSpend.trim() ? Number(minSpend) : null,
      maxUses: maxUses.trim() ? Number(maxUses) : null, days: Number(days) });
    if (ok) { setCode(''); setMinSpend(''); setMaxUses(''); }
  }
  return (
    <form className="promo-form" onSubmit={submit} aria-label="New promo code">
      <label htmlFor="pc-c">Code<input id="pc-c" className="rounded-input" value={code} onChange={(e) => setCode(e.target.value.toUpperCase())} required maxLength={20} placeholder="e.g. SPRING15" /></label>
      <label htmlFor="pc-pc">% off<input id="pc-pc" className="rounded-input" type="number" min={5} max={50} value={percent} onChange={(e) => setPercent(e.target.value)} required /></label>
      <label htmlFor="pc-min">Min spend (£)<input id="pc-min" className="rounded-input" inputMode="decimal" value={minSpend} onChange={(e) => setMinSpend(e.target.value)} placeholder="None" /></label>
      <label htmlFor="pc-max">Max uses<input id="pc-max" className="rounded-input" type="number" min={1} value={maxUses} onChange={(e) => setMaxUses(e.target.value)} placeholder="No limit" /></label>
      <label htmlFor="pc-d">Days<input id="pc-d" className="rounded-input" type="number" min={1} max={90} value={days} onChange={(e) => setDays(e.target.value)} required /></label>
      <button className="submit-btn" disabled={busy}>{busy ? 'Creating…' : 'Create code'}</button>
      {error && <div className="notice error promo-error" role="alert">{error}</div>}
    </form>
  );
}
