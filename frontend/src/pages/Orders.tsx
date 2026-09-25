import { useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { api } from '../api/client';
import type { Order, OrderStatus } from '../api/types';
import { useCart } from '../cart/CartContext';
import { OrderActivity, TrackingInfo } from '../components/OrderActivity';
import { ProductImage } from '../components/ProductImage';
import { ReturnsPanel } from '../components/ReturnsPanel';
import { dateOnly, dateTime, deliveryRange, money, statusLabel } from '../ui/format';
import { useToast } from '../ui/Toast';
import { useAsync } from '../ui/useAsync';

export function StatusPill({ status }: { status: string }) {
  return <span className={`status-pill status-${status}`}>{statusLabel(status)}</span>;
}

const HEADLINE: Record<OrderStatus, string> = {
  AWAITING_PAYMENT: 'Waiting for your card payment',
  PLACED: 'Order placed',
  PROCESSING: 'Being prepared',
  SHIPPED: 'On its way',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled',
};

const STEPS: OrderStatus[] = ['PLACED', 'PROCESSING', 'SHIPPED', 'DELIVERED'];

/** Placed -> Processing -> Shipped -> Delivered, with the current step highlighted. */
function Tracker({ status }: { status: OrderStatus }) {
  const at = STEPS.indexOf(status);
  if (at < 0) return null; // cancelled / awaiting payment have no progress to show
  return (
    <ol className="tracker" aria-label="Order progress">
      {STEPS.map((step, n) => (
        <li key={step} className={n < at ? 'done' : n === at ? 'now' : ''} aria-current={n === at ? 'step' : undefined}>
          <span className="dot" aria-hidden="true">{n < at ? '✓' : n + 1}</span>
          <span>{statusLabel(step)}</span>
        </li>
      ))}
    </ol>
  );
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

/** "Arriving Thu 1 Oct – Fri 2 Oct" while an order is on its way (dates promised at checkout). */
function Arriving({ o }: { o: Order }) {
  if (!o.deliveryFrom || !['PLACED', 'PROCESSING', 'SHIPPED'].includes(o.status)) return null;
  return <p className="order-note arriving">Arriving <b>{deliveryRange(o.deliveryFrom, o.deliveryTo)}</b>{o.deliveryOption === 'EXPRESS' ? ' · Express' : ''}</p>;
}

function OrderCard({ o }: { o: Order }) {
  return (
    <article className="order-card">
      <header className="order-head">
        <div><span>Order placed</span><b>{dateOnly(o.createdAt)}</b></div>
        <div><span>Total</span><b>{money(o.total)}</b></div>
        <div><span>Ship to</span><b>{o.address.name}</b></div>
        <div className="order-no"><span>Order # {o.id}</span><Link to={`/orders/${o.id}`}>View order details</Link></div>
      </header>
      <div className="order-body">
        <div className="order-main">
          <h2 className={`order-status s-${o.status}`}>{HEADLINE[o.status]}</h2>
          {o.status === 'AWAITING_PAYMENT' && <p className="order-note">Your items are reserved for a short time while you pay.</p>}
          {o.status === 'SHIPPED' && o.trackingNumber && <p className="order-note">{o.trackingCarrier ?? 'Tracking'}: <b>{o.trackingNumber}</b></p>}
          {o.status === 'DELIVERED' && o.deliveredAt && <p className="order-note">Delivered on {dateOnly(o.deliveredAt)}</p>}
          <Arriving o={o} />
          {o.returns.some((r) => r.status === 'REQUESTED' || r.status === 'APPROVED') && <p className="order-note">A return is in progress. <Link to={`/orders/${o.id}`}>See details</Link></p>}
          {o.items.map((i) => (
            <div key={i.productId} className="order-item">
              <Link to={`/products/${i.productId}`} className="order-thumb" aria-label={i.productName}>
                <ProductImage imageUrl={i.imageUrl} categoryName={i.categoryName} alt={i.productName} />
              </Link>
              <div>
                <Link to={`/products/${i.productId}`} className="order-item-name">{i.productName}</Link>
                <div className="order-item-sub">Sold by {o.sellerName} · Qty {i.quantity} · {money(i.unitPrice)} each{i.promotion && <> · <span className="promo-label">{i.promotion}</span></>}</div>
              </div>
            </div>
          ))}
        </div>
        <div className="order-actions">
          {o.status === 'AWAITING_PAYMENT' && o.checkoutRef && <Link to={`/pay/return?ref=${o.checkoutRef}`} className="cart-btn link-btn">Complete payment</Link>}
          <Link to={`/orders/${o.id}`} className="side-btn">View order</Link>
        </div>
      </div>
    </article>
  );
}

export function OrdersPage() {
  const { data, error, loading } = useAsync(() => api<Order[]>('/orders'), []);
  const placed = (useLocation().state as { placed?: number } | null)?.placed;
  return (
    <div className="orders-page">
      <h1>Your orders</h1>
      {placed && <div className="notice ok" role="status">Thank you! We've placed {placed} order{placed === 1 ? '' : 's'}, one per seller. You'll pay on delivery.</div>}
      {error && <div className="notice error">{error}</div>}
      {loading && !data ? <div className="loading">Loading…</div> : data && data.length === 0 ? (
        <div className="cart-empty">
          <div className="cart-empty-art" aria-hidden="true">📦</div>
          <div>
            <h2>No orders yet</h2>
            <p>When you place an order it will show up here.</p>
            <Link className="cart-btn big link-btn" to="/products">Start shopping</Link>
          </div>
        </div>
      ) : (
        <div className="orders-list">
          {data && groupByCheckout(data).map((group) => (
            <section key={group[0].checkoutRef ?? group[0].id} className="order-group">
              {group.length > 1 && <div className="order-group-note">Placed together on {dateTime(group[0].createdAt)}: {group.length} orders, {money(group.reduce((s, o) => s + o.total, 0))} in total</div>}
              {group.map((o) => <OrderCard key={o.id} o={o} />)}
            </section>
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
  const { refresh, add } = useCart();

  async function buyAgain(productId: number, name: string) {
    try {
      await add(productId, 1);
      toast.show(`Added ${name} to your cart`);
    } catch (e) {
      toast.show(e instanceof Error ? e.message : 'That item is no longer available.', 'error');
    }
  }
  const [busy, setBusy] = useState(false);
  const justPlaced = (location.state as { placed?: number } | null)?.placed;

  async function cancel() {
    if (!window.confirm(data?.paymentMethod === 'CARD' ? 'Cancel this order? The amount for this order will be refunded to your card.' : 'Cancel this order?')) return;
    setBusy(true);
    try {
      const updated = await api<Order>(`/orders/${id}/cancel`, { method: 'POST' });
      setData(() => updated);
      toast.show(updated.paymentMethod === 'CARD' ? 'Order cancelled. Your refund is on its way.' : 'Order cancelled');
    } catch (e) {
      toast.show(e instanceof Error ? e.message : 'Could not cancel the order.', 'error');
    } finally {
      setBusy(false);
      void refresh();
    }
  }

  if (error && !data) return <div className="orders-page"><div className="notice error">{error}</div><Link to="/orders" className="cart-btn link-btn" style={{ alignSelf: 'flex-start' }}>All orders</Link></div>;
  if (loading && !data) return <div className="loading">Loading…</div>;
  if (!data) return null;
  const o = data;
  const a = o.address;

  return (
    <div className="orders-page">
      <nav className="crumbs"><Link to="/orders">Your orders</Link> <span aria-hidden="true">›</span> Order #{o.id}</nav>
      <h1>Order details</h1>
      {justPlaced && <div className="notice ok" role="status">Thank you! Your order has been placed. You'll pay on delivery.</div>}
      {o.status === 'AWAITING_PAYMENT' && o.checkoutRef && (
        <div className="notice" role="status">
          This order is waiting for your card payment. Your items are reserved for a short time.{' '}
          <Link to={`/pay/return?ref=${o.checkoutRef}`}><b>Complete payment</b></Link>
        </div>
      )}

      <div className="order-card">
        <header className="order-head">
          <div><span>Ordered on</span><b>{dateTime(o.createdAt)}</b></div>
          <div><span>Order #</span><b>{o.id}</b></div>
          <div>
            <span>Sold and shipped by</span><b>{o.sellerSlug ? <Link to={`/sellers/${o.sellerSlug}`}>{o.sellerName}</Link> : o.sellerName}</b>
            {o.sellerSlug && <Link to={`/messages/new?seller=${o.sellerSlug}&order=${o.id}`} className="message-seller">Message the seller</Link>}
          </div>
          <div className="order-no"><StatusPill status={o.status} /></div>
        </header>
        <div className="order-detail-grid">
          <section>
            <h3>Delivering to</h3>
            <address>{a.name}<br />{a.line1}<br />{a.line2 && <>{a.line2}<br /></>}{a.city} {a.postcode}<br />{a.country}</address>
            <p className="muted" style={{ margin: '8px 0 0' }}>{o.deliveryLabel}</p>
            <Arriving o={o} />
          </section>
          <section>
            <h3>Payment</h3>
            <p>{o.paymentMethod === 'CARD' ? 'Card, paid online' : 'Pay on delivery'}</p>
          </section>
          <section className="order-summary">
            <h3>Order summary</h3>
            <div><span>Items</span><span>{money(o.subtotal)}</span></div>
            <div><span>Delivery</span><span>{o.shipping === 0 ? 'FREE' : money(o.shipping)}</span></div>
            <div className="grand"><span>Order total</span><span>{money(o.total)}</span></div>
          </section>
        </div>
      </div>

      <div className="order-card">
        <div className="order-body single">
          <div className="order-main">
            <h2 className={`order-status s-${o.status}`}>{HEADLINE[o.status]}</h2>
            <Tracker status={o.status} />
            <TrackingInfo order={o} />
            {o.items.map((i) => (
              <div key={i.productId} className="order-item">
                <Link to={`/products/${i.productId}`} className="order-thumb" aria-label={i.productName}>
                  <ProductImage imageUrl={i.imageUrl} categoryName={i.categoryName} alt={i.productName} />
                </Link>
                <div>
                  <Link to={`/products/${i.productId}`} className="order-item-name">{i.productName}</Link>
                  <div className="order-item-sub">
                    Qty {i.quantity} · {money(i.unitPrice)} each
                    {i.listUnitPrice != null && <> <s className="was">{money(i.listUnitPrice)}</s> <span className="promo-label">{i.promotion}</span></>}
                  </div>
                  <div className="order-item-sub"><b>{money(i.lineTotal)}</b></div>
                  {o.status === 'DELIVERED' && <button className="cart-btn buy-again" onClick={() => void buyAgain(i.productId, i.productName)}>Buy it again</button>}
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      <ReturnsPanel order={o} onChange={(u) => setData(() => u)} />

      <div className="order-card">
        <div className="order-body single">
          <div className="order-main">
            <h2 className="order-status">Order activity</h2>
            <OrderActivity order={o} />
          </div>
        </div>
      </div>

      {o.status === 'DELIVERED' && (
        <div className="notice">
          Delivered! <Link to="/account/reviews">Review what you bought</Link>
          {o.sellerSlug && <> or <Link to={`/sellers/${o.sellerSlug}`}>rate {o.sellerName}</Link></>}.
        </div>
      )}
      {o.cancellableByCustomer && <div><button className="side-btn" onClick={cancel} disabled={busy}>{busy ? 'Cancelling…' : 'Cancel order'}</button></div>}
    </div>
  );
}
