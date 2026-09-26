import { useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../../api/client';
import type { Earnings, OrderStatus, Page, Product, Seller, SellerQuestion, SellerStats } from '../../api/types';
import { Pagination } from '../../components/Pagination';
import { ProductImage } from '../../components/ProductImage';
import { dateOnly, dateTime, money } from '../../ui/format';
import { useToast } from '../../ui/Toast';
import { useAsync } from '../../ui/useAsync';
import { useSeller } from '../../seller/SellerContext';
import { StockCell, toInput } from '../admin/AdminProducts';
import { OrdersManager } from '../admin/AdminOrders';
import { StatusPill } from '../Orders';

const LEDGER_LABEL: Record<string, string> = { SALE: 'Sale', COMMISSION: 'Commission', PAYOUT: 'Payout', REFUND: 'Refund', COMMISSION_REFUND: 'Commission returned' };
const cap = (s: string) => LEDGER_LABEL[s] ?? s.charAt(0) + s.slice(1).toLowerCase();

export function SellerDashboard() {
  const { seller } = useSeller();
  const { data, error } = useAsync(() => api<SellerStats>('/seller/stats'), []);
  const s = data;
  return (
    <div className="page">
      <div className="row-wrap">
        <div>
          <h1 className="page-title">Seller Central</h1>
          {seller && <p className="page-subtitle">{seller.storeName} · <Link to={`/sellers/${seller.slug}`}>view your storefront</Link></p>}
        </div>
        <span className="spacer" />
        <Link className="submit-btn" to="/seller/products/new">+ List a product</Link>
      </div>
      {error && <div className="notice error">{error}</div>}
      {!s ? <div className="loading">Loading…</div> : (
        <>
          {s.ordersByStatus.PLACED > 0 && (
            <Link to="/seller/orders?status=PLACED" className="notice" style={{ textDecoration: 'none' }}>
              📦 <b>{s.ordersByStatus.PLACED}</b> new order{s.ordersByStatus.PLACED === 1 ? '' : 's'} waiting to be processed →
            </Link>
          )}
          {s.openReturns > 0 && (
            <Link to="/seller/returns" className="notice" style={{ textDecoration: 'none' }}>
              ↩ <b>{s.openReturns}</b> return request{s.openReturns === 1 ? '' : 's'} waiting for your decision →
            </Link>
          )}
          {s.unansweredQuestions > 0 && (
            <Link to="/seller/questions" className="notice" style={{ textDecoration: 'none' }}>
              💬 <b>{s.unansweredQuestions}</b> customer question{s.unansweredQuestions === 1 ? '' : 's'} to answer →
            </Link>
          )}
          <div className="kpi-grid">
            <Link to="/seller/earnings" className="square-review-box kpi" style={{ textDecoration: 'none' }}><span className="value">{money(s.balance)}</span><span className="label">Balance to be paid out</span></Link>
            <div className="square-review-box kpi static"><span className="value">{money(s.grossSales)}</span><span className="label">Sales (excl. cancelled)</span></div>
            <Link to="/seller/orders" className="square-review-box kpi" style={{ textDecoration: 'none' }}><span className="value">{s.orderCount}</span><span className="label">Orders · {s.unitsSold} units</span></Link>
            <Link to="/seller/products" className="square-review-box kpi" style={{ textDecoration: 'none' }}><span className="value">{s.productCount}</span><span className="label">Products listed</span></Link>
          </div>

          <section className="stack" style={{ gap: 12 }}>
            <h2 className="page-title" style={{ fontSize: 24 }}>Orders by status</h2>
            <div className="row-wrap">
              {(Object.keys(s.ordersByStatus) as OrderStatus[]).map((st) => (
                <Link key={st} to={`/seller/orders?status=${st}`} className="chip" style={{ display: 'inline-flex', gap: 8 }}>{cap(st)} <b>{s.ordersByStatus[st]}</b></Link>
              ))}
            </div>
          </section>

          {s.lowStock.length > 0 && (
            <section className="stack" style={{ gap: 12 }}>
              <h2 className="page-title" style={{ fontSize: 24 }}>Low stock</h2>
              <div className="square-review-box static">
                {s.lowStock.map((p) => (
                  <div className="rating-row" key={p.productId}>
                    <span className="name">{p.name}</span>
                    <span className={p.stock === 0 ? 'error-text' : 'avg-label'}>{p.stock === 0 ? 'Out of stock' : `${p.stock} left`}</span>
                    <Link to={`/seller/products/${p.productId}`} className="view-all-link">Restock</Link>
                  </div>
                ))}
              </div>
            </section>
          )}
        </>
      )}
    </div>
  );
}

export function SellerProducts() {
  const [params, setParams] = useSearchParams();
  const q = params.get('q') ?? '';
  const page = Number(params.get('page') ?? '0') || 0;
  const toast = useToast();
  const products = useAsync(() => api<Page<Product>>('/seller/products', { query: { q, page, size: 15 } }), [q, page]);

  async function toggle(p: Product) {
    try {
      if (p.active) await api(`/seller/products/${p.id}`, { method: 'DELETE' });
      else await api(`/seller/products/${p.id}`, { method: 'PUT', body: toInput(p, { active: true }) });
      toast.show(p.active ? 'Listing hidden from the store' : 'Listing is live again');
      products.reload();
    } catch (e) {
      toast.show(e instanceof Error ? e.message : 'Action failed.', 'error');
    }
  }

  return (
    <div className="page">
      <div className="row-wrap">
        <div>
          <h1 className="page-title">Your products</h1>
          {products.data && <p className="page-subtitle">{products.data.totalItems} listing{products.data.totalItems === 1 ? '' : 's'}</p>}
        </div>
        <span className="spacer" />
        <input className="rounded-input" style={{ width: 240 }} placeholder="Search your listings…" value={q} onChange={(e) => setParams(e.target.value ? { q: e.target.value } : {})} aria-label="Search your listings" />
        <Link to="/seller/products/new" className="submit-btn">+ List a product</Link>
      </div>
      {products.error && <div className="notice error">{products.error}</div>}
      <div className="square-review-box static table-wrap">
        <table className="data">
          <thead><tr><th>Product</th><th>Category</th><th>Price</th><th>Stock</th><th>Rating</th><th>Status</th><th></th></tr></thead>
          <tbody>
            {products.data?.items.map((p) => (
              <tr key={p.id} style={{ opacity: p.active ? 1 : 0.6 }}>
                <td><span className="row"><span className="thumb"><ProductImage imageUrl={p.imageUrl} categoryName={p.category?.name} alt="" /></span><b>{p.name}</b>{p.catalogId !== p.id && <span className="chip small">Offer</span>}</span></td>
                <td>{p.category?.name ?? <span className="muted">—</span>}</td>
                <td>{money(p.price)}{p.discountPercent > 0 && <span className="deal-badge" style={{ marginLeft: 6 }}>-{p.discountPercent}%</span>}</td>
                <td><StockCell key={`${p.id}-${p.stock}`} product={p} base="/seller/products" onSaved={products.reload} /></td>
                <td>{p.ratingCount ? `${p.ratingAvg.toFixed(1)} ★ (${p.ratingCount})` : <span className="muted">—</span>}</td>
                <td>{p.active ? <span className="badge ok">Live</span> : <span className="badge warn">Hidden</span>}</td>
                <td><span className="row" style={{ gap: 8 }}>
                  <Link to={`/seller/products/${p.id}`} className="mini-icon-btn" style={{ textDecoration: 'none', color: 'inherit' }} title="Edit" aria-label={`Edit ${p.name}`}>✎</Link>
                  <button className={`mini-icon-btn ${p.active ? 'delete-btn' : ''}`} onClick={() => toggle(p)} title={p.active ? 'Hide listing' : 'Show listing'} aria-label={p.active ? `Hide ${p.name}` : `Show ${p.name}`}>{p.active ? '🗑' : '↺'}</button>
                </span></td>
              </tr>
            ))}
          </tbody>
        </table>
        {products.data && products.data.items.length === 0 && <div className="empty">{q ? 'No listings match.' : <>You haven't listed anything yet. <Link to="/seller/products/new">List your first product</Link></>}</div>}
      </div>
      {products.data && <Pagination page={products.data.page} totalPages={products.data.totalPages} onChange={(p) => setParams({ ...(q ? { q } : {}), page: String(p) })} />}
    </div>
  );
}

export const SellerOrders = () => <OrdersManager base="seller" />;

export function SellerEarnings() {
  const [page, setPage] = useState(0);
  const { data, error } = useAsync(() => api<Earnings>('/seller/earnings', { query: { page, size: 15 } }), [page]);
  return (
    <div className="page">
      <div>
        <h1 className="page-title">Earnings</h1>
        <p className="page-subtitle">Recorded when an order is delivered. Refunds for returns come off your balance, and Pacific gives back its commission on them.</p>
      </div>
      {error && <div className="notice error">{error}</div>}
      {!data ? <div className="loading">Loading…</div> : (
        <>
          <div className="kpi-grid">
            <div className="square-review-box kpi static"><span className="value">{money(data.balance)}</span><span className="label">Current balance</span></div>
            <div className="square-review-box kpi static"><span className="value">{money(data.sales)}</span><span className="label">Sales incl. shipping</span></div>
            <div className="square-review-box kpi static"><span className="value">{money(data.commission)}</span><span className="label">Commission kept by Pacific</span></div>
            <div className="square-review-box kpi static"><span className="value">{money(data.refunds)}</span><span className="label">Refunded to customers</span></div>
            <div className="square-review-box kpi static"><span className="value">{money(data.payouts)}</span><span className="label">Paid out so far</span></div>
          </div>
          <div className="square-review-box static table-wrap">
            <table className="data">
              <thead><tr><th>When</th><th>Type</th><th>Details</th><th style={{ textAlign: 'right' }}>Amount</th></tr></thead>
              <tbody>
                {data.entries.items.map((e) => (
                  <tr key={e.id}><td>{dateTime(e.createdAt)}</td><td>{cap(e.type)}</td><td>{e.note ?? ''}</td><td style={{ textAlign: 'right', color: e.amount < 0 ? 'var(--danger)' : 'inherit', fontWeight: 'bold' }}>{money(e.amount)}</td></tr>
                ))}
              </tbody>
            </table>
            {data.entries.items.length === 0 && <div className="empty">Nothing yet — earnings appear once an order is delivered.</div>}
          </div>
          <Pagination page={data.entries.page} totalPages={data.entries.totalPages} onChange={setPage} />
        </>
      )}
    </div>
  );
}

export function SellerQuestions() {
  const toast = useToast();
  const { data, error, reload } = useAsync(() => api<SellerQuestion[]>('/seller/questions'), []);
  const [active, setActive] = useState<number | null>(null);
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);

  async function answer(e: FormEvent, id: number) {
    e.preventDefault();
    setBusy(true);
    try {
      await api(`/questions/${id}/answers`, { method: 'POST', body: { text: text.trim() } });
      toast.show('Answer posted');
      setActive(null);
      setText('');
      reload();
    } catch (err) {
      toast.show(err instanceof Error ? err.message : 'Could not post your answer.', 'error');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="page page-narrow">
      <div>
        <h1 className="page-title">Customer questions</h1>
        <p className="page-subtitle">Questions on your products that nobody has answered yet</p>
      </div>
      {error && <div className="notice error">{error}</div>}
      {data && data.length === 0 && <div className="empty">You're all caught up. 🎉</div>}
      <div className="stack">
        {data?.map((q) => (
          <div key={q.id} className="square-review-box static stack">
            <div><b>Q:</b> {q.text}</div>
            <div className="muted" style={{ fontSize: 12 }}>{q.askerName} · {dateOnly(q.createdAt)} · on <Link to={`/products/${q.productId}`}>{q.productName}</Link></div>
            {active === q.id ? (
              <form className="row" onSubmit={(e) => answer(e, q.id)}>
                <input className="rounded-input" autoFocus placeholder="Write your answer…" value={text} onChange={(e) => setText(e.target.value)} maxLength={500} aria-label="Your answer" />
                <button className="submit-btn" disabled={busy || !text.trim()}>Post</button>
                <button type="button" className="ghost-btn" onClick={() => setActive(null)}>Cancel</button>
              </form>
            ) : <div><button className="submit-btn" onClick={() => { setActive(q.id); setText(''); }}>Answer</button></div>}
          </div>
        ))}
      </div>
    </div>
  );
}

export function SellerSettings() {
  const { seller, refresh } = useSeller();
  const toast = useToast();
  const [storeName, setStoreName] = useState(seller?.storeName ?? '');
  const [description, setDescription] = useState(seller?.description ?? '');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  if (!seller) return null;

  async function save(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      await api<Seller>('/seller/me', { method: 'PUT', body: { storeName: storeName.trim(), description: description.trim() || null } });
      await refresh();
      toast.show('Store details saved');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not save.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="page page-narrow" onSubmit={save}>
      <h1 className="page-title">Store settings</h1>
      <div className="square-review-box static stack">
        <div className="row-wrap"><span>Status</span><StatusPill status={seller.status} /><span className="spacer" /><span className="muted">Your storefront: <Link to={`/sellers/${seller.slug}`}>/sellers/{seller.slug}</Link></span></div>
        <div className="notice">Pacific's commission on your item sales is <b>{seller.commissionPercent}%</b>{seller.commissionOverridden ? ' (a rate agreed for your store)' : ''}. It's fixed when each order is placed.</div>
        <label className="field-label small" htmlFor="ss-n">Store name</label>
        <input id="ss-n" className="rounded-input" value={storeName} onChange={(e) => setStoreName(e.target.value)} required minLength={3} maxLength={80} />
        <label className="field-label small" htmlFor="ss-d">Description</label>
        <textarea id="ss-d" className="rounded-input" value={description} onChange={(e) => setDescription(e.target.value)} maxLength={1000} />
        {error && <div className="notice error" role="alert">{error}</div>}
        <div><button className="submit-btn" disabled={busy}>{busy ? 'Saving…' : 'Save changes'}</button></div>
      </div>
    </form>
  );
}
