import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import type { Product } from '../api/types';
import { money } from '../ui/format';
import { useToast } from '../ui/Toast';
import { PhotoField } from './PhotoField';
import { ProductImage } from './ProductImage';

const PRICE = /^\d{1,8}(\.\d{1,2})?$/;

/**
 * Seller Central (and admin) panel on a product's edit page: sell it in other colours, sizes and so on. Each
 * variation is a product of its own (edited like any other); this sets up the family and adds variations to it.
 */
export function VariationsPanel({ base, product, onChanged }: { base: string; product: Product; onChanged: () => void }) {
  const toast = useToast();
  const v = product.variations;
  const [dim1, setDim1] = useState(v?.dim1 ?? '');
  const [dim2, setDim2] = useState(v?.dim2 ?? '');
  const [own1, setOwn1] = useState(v?.option1 ?? '');
  const [own2, setOwn2] = useState(v?.option2 ?? '');
  const [new1, setNew1] = useState('');
  const [new2, setNew2] = useState('');
  const [price, setPrice] = useState(product.price.toFixed(2));
  const [stock, setStock] = useState('0');
  const [photo, setPhoto] = useState('');
  const [uploading, setUploading] = useState(false);
  const [editingNames, setEditingNames] = useState(!v);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function run(fn: () => Promise<unknown>, done: string) {
    setBusy(true);
    setError('');
    try {
      await fn();
      toast.show(done);
      onChanged();
      return true;
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong.');
      return false;
    } finally {
      setBusy(false);
    }
  }

  function saveFamily(e: FormEvent) {
    e.preventDefault();
    void run(() => api(`${base}/${product.id}/family`, { method: 'PUT', body: { dim1, dim2: dim2.trim() || null, option1: own1, option2: own2.trim() || null } }),
      v ? 'Variation details saved' : 'Variations set up: now add the other options')
      .then((ok) => ok && setEditingNames(false));
  }

  function addVariation(e: FormEvent) {
    e.preventDefault();
    if (!PRICE.test(price.trim()) || Number(price) <= 0) return setError('Enter a price like 19.99.');
    const n = Number(stock);
    if (!Number.isInteger(n) || n < 0) return setError('Stock must be a whole number, 0 or more.');
    void run(() => api(`${base}/${product.id}/variations`, { method: 'POST', body: { option1: new1, option2: new2.trim() || null, price: Number(price), stock: n, imageUrl: photo.trim() || null } }),
      'Variation added').then((ok) => { if (ok) { setNew1(''); setNew2(''); setPhoto(''); } });
  }

  function leave() {
    if (!window.confirm(`Take ${product.name} (${product.variation}) out of its variations? It stays on sale as a product of its own.`)) return;
    void run(() => api(`${base}/${product.id}/family`, { method: 'DELETE' }), 'Removed from the variations');
  }

  const twoDims = !!(v ? v.dim2 : dim2.trim());
  return (
    <section className="square-review-box static stack variations-panel" aria-labelledby="variations-h">
      <div>
        <h2 className="section-title" id="variations-h" style={{ fontSize: 18 }}>Variations</h2>
        <p className="muted" style={{ margin: '4px 0 0' }}>
          {v ? <>Shoppers see one product and pick a {v.dim1.toLowerCase()}{v.dim2 && <> and {v.dim2.toLowerCase()}</>}. Each variation has its own price, stock and photos.</>
            : <>Sell this in other colours, sizes and so on. Shoppers see one product with a picker; each option has its own price, stock and photos.</>}
        </p>
      </div>

      {v && (
        <div className="table-wrap">
          <table className="data var-table">
            <thead><tr><th>Option</th><th>Price</th><th>Stock</th><th>Status</th><th></th></tr></thead>
            <tbody>
              {v.options.map((o) => (
                <tr key={o.productId}>
                  <td><span className="row"><span className="thumb"><ProductImage imageUrl={o.imageUrl} categoryName={product.category?.name} alt="" /></span>
                    <b>{o.option1}{o.option2 && <> / {o.option2}</>}</b>{o.productId === product.id && <span className="chip small">This one</span>}</span></td>
                  <td>{money(o.price)}</td>
                  <td>{o.inStock ? 'In stock' : <span className="muted">Sold out</span>}</td>
                  <td>{o.active ? <span className="badge ok">Live</span> : <span className="badge warn">Hidden</span>}</td>
                  <td>{o.productId !== product.id && <Link to={`${base}/${o.productId}`} className="mini-icon-btn" style={{ textDecoration: 'none', color: 'inherit' }} title="Edit" aria-label={`Edit ${o.option1}${o.option2 ? ` / ${o.option2}` : ''}`}>✎</Link>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {editingNames ? (
        <form className="form-grid" onSubmit={saveFamily} aria-label="What the variations differ by">
          <div className="form-field"><label className="field-label small" htmlFor="vd1">Varies by</label>
            <input id="vd1" className="rounded-input" value={dim1} onChange={(e) => setDim1(e.target.value)} placeholder="e.g. Colour" maxLength={30} required /></div>
          <div className="form-field"><label className="field-label small" htmlFor="vd2">And by (optional)</label>
            <input id="vd2" className="rounded-input" value={dim2} onChange={(e) => setDim2(e.target.value)} placeholder="e.g. Size" maxLength={30} /></div>
          <div className="form-field"><label className="field-label small" htmlFor="vo1">This product's {dim1.trim().toLowerCase() || 'option'}</label>
            <input id="vo1" className="rounded-input" value={own1} onChange={(e) => setOwn1(e.target.value)} placeholder="e.g. Red" maxLength={40} required /></div>
          {dim2.trim() && <div className="form-field"><label className="field-label small" htmlFor="vo2">This product's {dim2.trim().toLowerCase()}</label>
            <input id="vo2" className="rounded-input" value={own2} onChange={(e) => setOwn2(e.target.value)} placeholder="e.g. M" maxLength={40} required /></div>}
          <div className="form-field full" style={{ display: 'flex', flexDirection: 'row', justifyContent: 'flex-end', gap: 12 }}>
            {v && <button type="button" className="cart-link-btn" onClick={() => setEditingNames(false)}>Cancel</button>}
            <button className="submit-btn" disabled={busy}>{v ? 'Save' : 'Set up variations'}</button>
          </div>
        </form>
      ) : v && (
        <div className="row-wrap">
          <button type="button" className="cart-link-btn" onClick={() => setEditingNames(true)}>Change this one's options or rename {v.dim2 ? 'them' : v.dim1.toLowerCase()}</button>
          <button type="button" className="cart-link-btn" onClick={leave} disabled={busy}>Remove this one from the variations</button>
        </div>
      )}

      {v && (
        <form className="form-grid" onSubmit={addVariation} aria-label="Add a variation">
          <h3 className="form-field full" style={{ margin: 0, fontSize: 15 }}>Add a variation</h3>
          <div className="form-field"><label className="field-label small" htmlFor="vn1">{v.dim1}</label>
            <input id="vn1" className="rounded-input" value={new1} onChange={(e) => setNew1(e.target.value)} maxLength={40} required /></div>
          {twoDims && <div className="form-field"><label className="field-label small" htmlFor="vn2">{v.dim2}</label>
            <input id="vn2" className="rounded-input" value={new2} onChange={(e) => setNew2(e.target.value)} maxLength={40} required /></div>}
          <div className="form-field"><label className="field-label small" htmlFor="vnp">Price (£)</label>
            <input id="vnp" className="rounded-input" inputMode="decimal" value={price} onChange={(e) => setPrice(e.target.value)} required /></div>
          <div className="form-field"><label className="field-label small" htmlFor="vns">Stock</label>
            <input id="vns" className="rounded-input" type="number" min={0} value={stock} onChange={(e) => setStock(e.target.value)} required /></div>
          <div className="form-field full"><span className="field-label small">Photo (optional: otherwise this product's photos are used)</span>
            <PhotoField value={photo} onChange={setPhoto} onBusyChange={setUploading} name={`${product.name} ${new1}`} categoryName={product.category?.name} /></div>
          <div className="form-field full" style={{ display: 'flex', flexDirection: 'row', justifyContent: 'flex-end', gap: 12 }}>
            <button className="submit-btn" disabled={busy || uploading}>{uploading ? 'Uploading photo…' : 'Add variation'}</button>
          </div>
        </form>
      )}
      {error && <div className="notice error" role="alert">{error}</div>}
    </section>
  );
}
