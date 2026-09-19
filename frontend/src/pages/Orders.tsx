import { useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { api } from '../api/client';
import type { Order } from '../api/types';
import { useCart } from '../cart/CartContext';
import { dateTime, money } from '../ui/format';
import { useToast } from '../ui/Toast';
import { useAsync } from '../ui/useAsync';

export function StatusPill({ status }: { status: string }) {
  return <span className={`status-pill status-${status}`}>{status.charAt(0) + status.slice(1).toLowerCase()}</span>;
}

/** Orders from one checkout are shown together, since a basket with several sellers becomes several orders. */
function groupByCheckout(orders: Order[]): Order[][] {
  const groups = new Map<string, Order[]>();
  orders.forEach((o) => {
    const key = o.checkoutRef ?? `single-${o.id}`;
    groups.set(key, [...(groups.get(key) ?? []), o]);
  });
  return [...groups.values()];
}

export function OrdersPage() {
  const { data, error, loading } = useAsync(() => api<Order[]>('/orders'), []);
  const placed = (useLocation().state as { placed?: number } | null)?.placed;
  return (
    <div className="page page-narrow">
      <h1 className="page-title">Returns and Orders</h1>
      {placed && <div className="notice ok" role="status">Thank you! We've placed {placed} order{placed === 1 ? '' : 's'} — one per seller. You'll pay on delivery.</div>}
      {error && <div className="notice error">{error}</div>}
      {loading && !data ? <div className="loading">Loading…</div> : data && data.length === 0 ? (
        <div className="empty"><p>You haven't placed any orders yet.</p><Link className="submit-btn" to="/products">Start shopping</Link></div>
      ) : (
        <div className="stack">
          {data && groupByCheckout(data).map((group) => (
            <div key={group[0].checkoutRef ?? group[0].id} className="stack" style={{ gap: 8 }}>
              {group.length > 1 && <div className="muted" style={{ fontSize: 12 }}>Placed together · {dateTime(group[0].createdAt)} · {money(group.reduce((s, o) => s + o.total, 0))} in total</div>}
              {group.map((o) => (
                <Link key={o.id} to={`/orders/${o.id}`} className="square-review-box row-wrap" style={{ textDecoration: 'none', color: 'inherit' }}>
                  <div>
                    <div style={{ fontWeight: 'bold' }}>Order #{o.id} <span className="muted" style={{ fontWeight: 'normal' }}>· Sold by {o.sellerName}</span></div>
                    <div className="muted">{dateTime(o.createdAt)} · {o.itemCount} item{o.itemCount === 1 ? '' : 's'}</div>
                    <div className="muted" style={{ fontSize: 12 }}>{o.items.map((i) => i.productName).join(', ')}</div>
                  </div>
                  <span className="spacer" />
                  <StatusPill status={o.status} />
                  <span className="product-price">{money(o.total)}</span>
                </Link>
              ))}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export function OrderDetail() {
  const { id } = useParams();
  const location = useLocation();
  const { data, error, loading, setData } = useAsync(() => api<Order>(`/orders/${id}`), [id]);
  const toast = useToast();
  const { refresh } = useCart();
  const [busy, setBusy] = useState(false);
  const justPlaced = (location.state as { placed?: number } | null)?.placed;

  async function cancel() {
    if (!window.confirm('Cancel this order?')) return;
    setBusy(true);
    try {
      const updated = await api<Order>(`/orders/${id}/cancel`, { method: 'POST' });
      setData(() => updated);
      toast.show('Order cancelled');
    } catch (e) {
      toast.show(e instanceof Error ? e.message : 'Could not cancel the order.', 'error');
    } finally {
      setBusy(false);
      void refresh();
    }
  }

  if (error && !data) return <div className="page page-narrow"><div className="notice error">{error}</div><Link to="/orders" className="submit-btn" style={{ alignSelf: 'flex-start' }}>All orders</Link></div>;
  if (loading && !data) return <div className="loading">Loading…</div>;
  if (!data) return null;
  const o = data;

  return (
    <div className="page page-narrow">
      <div className="row">
        <Link to="/orders" className="back-btn" aria-label="Back to orders">←</Link>
        <h1 className="page-title">Order #{o.id}</h1>
        <StatusPill status={o.status} />
      </div>
      {justPlaced && <div className="notice ok" role="status">Thank you! Your order has been placed. You'll pay on delivery.</div>}
      <div className="square-review-box static stack">
        <div className="muted">
          Placed {dateTime(o.createdAt)} · Sold and shipped by{' '}
          {o.sellerSlug ? <Link to={`/sellers/${o.sellerSlug}`}>{o.sellerName}</Link> : <b>{o.sellerName}</b>}
        </div>
        {o.items.map((i) => (
          <div key={i.productId} className="row">
            <Link to={`/products/${i.productId}`} style={{ fontWeight: 'bold' }}>{i.productName}</Link>
            <span className="muted">{i.quantity} × {money(i.unitPrice)}</span>
            <span className="spacer" />
            <span>{money(i.lineTotal)}</span>
          </div>
        ))}
        <hr />
        <div className="totals">
          <div className="line"><span>Subtotal</span><span>{money(o.subtotal)}</span></div>
          <div className="line"><span>Shipping</span><span>{o.shipping === 0 ? 'FREE' : money(o.shipping)}</span></div>
          <div className="line grand"><span>Total</span><span>{money(o.total)}</span></div>
        </div>
      </div>
      <div className="square-review-box static">
        <h2 style={{ marginTop: 0 }}>Delivering to</h2>
        <address style={{ fontStyle: 'normal' }}>
          {o.address.name}<br />{o.address.line1}<br />{o.address.line2 && <>{o.address.line2}<br /></>}
          {o.address.city} {o.address.postcode}<br />{o.address.country}
        </address>
      </div>
      {o.status === 'DELIVERED' && (
        <div className="notice">
          Delivered! <Link to="/account/reviews">Review what you bought</Link>
          {o.sellerSlug && <> or <Link to={`/sellers/${o.sellerSlug}`}>rate {o.sellerName}</Link></>}.
        </div>
      )}
      {o.cancellableByCustomer && <div><button className="ghost-btn" onClick={cancel} disabled={busy}>{busy ? 'Cancelling…' : 'Cancel order'}</button></div>}
    </div>
  );
}
