import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import { CONDITION_LABELS, type ItemCondition, type Offer } from '../api/types';
import { money } from '../ui/format';
import { Stars } from './Stars';

/** The other sellers of a product (everyone but the buy box), each with their own "Add to cart". */
export function OtherSellers({ offers, onAdd, busyId, inCart }: {
  offers: Offer[];
  onAdd: (offer: Offer) => void;
  busyId: number | null;
  inCart: (productId: number) => number;
}) {
  if (offers.length === 0) return null;
  const lowest = Math.min(...offers.map((o) => o.price));
  return (
    <section className="other-sellers" aria-labelledby="other-sellers-h">
      <h2 id="other-sellers-h" className="other-sellers-h">
        Other sellers on Pacific <span className="muted">({offers.length}) from {money(lowest)}</span>
      </h2>
      <ul>
        {offers.map((o) => (
          <li key={o.productId} className="offer-row">
            <div className="offer-price">
              <b>{money(o.price)}</b>
              <span className={`offer-condition${o.condition === 'NEW' ? '' : ' used'}`}>{o.conditionLabel}</span>
            </div>
            <div className="offer-seller">
              <span>Sold by {o.sellerSlug ? <Link to={`/sellers/${o.sellerSlug}`}>{o.sellerName}</Link> : <b>{o.sellerName}</b>}</span>
              {o.sellerRatingCount > 0 && <Stars value={o.sellerRating} count={o.sellerRatingCount} />}
              <span className={`offer-stock${o.stock === 0 ? ' out' : o.stock <= 5 ? ' low' : ''}`}>
                {o.stock === 0 ? 'Out of stock' : o.stock <= 5 ? `Only ${o.stock} left` : 'In stock'}
              </span>
            </div>
            <button className="cart-btn" onClick={() => onAdd(o)} disabled={o.stock === 0 || busyId !== null || inCart(o.productId) >= Math.min(o.stock, 10)}
              aria-label={`Add to cart from ${o.sellerName} at ${money(o.price)}`}>
              {busyId === o.productId ? 'Adding…' : 'Add to cart'}
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}

/** An approved seller starts selling this product with their own price, stock and condition. */
export function SellThisToo({ productId, onDone }: { productId: number; onDone: () => void }) {
  const [open, setOpen] = useState(false);
  const [price, setPrice] = useState('');
  const [stock, setStock] = useState('1');
  const [condition, setCondition] = useState<ItemCondition>('NEW');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!/^\d{1,8}(\.\d{1,2})?$/.test(price.trim()) || Number(price) <= 0) return setError('Enter a price like 19.99.');
    setBusy(true);
    setError('');
    try {
      await api(`/seller/offers/${productId}`, { method: 'POST', body: { price: Number(price), stock: Math.max(0, Math.floor(Number(stock) || 0)), condition } });
      setOpen(false);
      onDone();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not add your offer.');
    } finally {
      setBusy(false);
    }
  }

  if (!open) {
    return (
      <div className="sell-this">
        <span>Have one to sell?</span>
        <button type="button" className="ghost-btn" onClick={() => setOpen(true)}>Sell on Pacific</button>
      </div>
    );
  }
  return (
    <form className="sell-this open" onSubmit={submit}>
      <b>Sell this product</b>
      <span className="muted" style={{ fontSize: 13 }}>Your offer joins this page; shoppers see the best offer first.</span>
      <div className="sell-this-fields">
        <label>Price (£)<input className="rounded-input" inputMode="decimal" value={price} onChange={(e) => setPrice(e.target.value)} required placeholder="e.g. 24.99" /></label>
        <label>Stock<input className="rounded-input" type="number" min={0} value={stock} onChange={(e) => setStock(e.target.value)} required /></label>
        <label>Condition
          <select className="rounded-input" value={condition} onChange={(e) => setCondition(e.target.value as ItemCondition)}>
            {(Object.keys(CONDITION_LABELS) as ItemCondition[]).map((c) => <option key={c} value={c}>{CONDITION_LABELS[c]}</option>)}
          </select>
        </label>
      </div>
      {error && <div className="notice error" role="alert">{error}</div>}
      <div className="row-wrap">
        <button className="submit-btn" disabled={busy}>{busy ? 'Adding…' : 'Add my offer'}</button>
        <button type="button" className="ghost-btn" onClick={() => setOpen(false)}>Cancel</button>
      </div>
    </form>
  );
}
