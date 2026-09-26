import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../../api/client';
import type { Order, OrderStatus, Page } from '../../api/types';
import { OrderActivity, TrackingInfo } from '../../components/OrderActivity';
import { Pagination } from '../../components/Pagination';
import { CARRIERS } from '../../ui/carriers';
import { dateTime, deliveryRange, money, statusLabel } from '../../ui/format';
import { useToast } from '../../ui/Toast';
import { useAsync } from '../../ui/useAsync';
import { StatusPill } from '../Orders';

const STATUSES: OrderStatus[] = ['AWAITING_PAYMENT', 'PLACED', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED'];

function OrderRow({ order, onChanged, base }: { order: Order; onChanged: (o: Order) => void; base: 'admin' | 'seller' }) {
  const toast = useToast();
  const [next, setNext] = useState<OrderStatus | ''>('');
  const [busy, setBusy] = useState(false);
  const [carrier, setCarrier] = useState('');
  const [tracking, setTracking] = useState('');

  async function apply() {
    if (!next) return;
    if (next === 'CANCELLED' && !window.confirm(`Cancel order #${order.id}? Stock will be returned.`)) return;
    setBusy(true);
    try {
      const body = next === 'SHIPPED' ? { status: next, carrier: carrier.trim() || null, trackingNumber: tracking.trim() || null } : { status: next };
      onChanged(await api<Order>(`/${base}/orders/${order.id}/status`, { method: 'PATCH', body }));
      setNext('');
      setCarrier('');
      setTracking('');
      toast.show(`Order #${order.id} is now ${next.toLowerCase()}`);
    } catch (e) {
      toast.show(e instanceof Error ? e.message : 'Could not update the order.', 'error');
    } finally {
      setBusy(false);
    }
  }

  return (
    <details className="square-review-box static">
      <summary className="row-wrap" style={{ cursor: 'pointer', listStyle: 'none' }}>
        <b>#{order.id}</b>
        <span>{order.customerName}</span>
        {base === 'admin' && <span className="muted">· {order.sellerName}</span>}
        <span className="muted">{dateTime(order.createdAt)}</span>
        <span className="spacer" />
        <StatusPill status={order.status} />
        <b>{money(order.total)}</b>
      </summary>
      <div className="stack" style={{ marginTop: 15 }}>
        <hr />
        {order.items.map((i) => (
          <div key={i.productId} className="row"><span>{i.quantity} × {i.productName}</span><span className="spacer" /><span>{money(i.lineTotal)}</span></div>
        ))}
        <div className="muted">
          Ship to: {order.address.name}, {order.address.line1}{order.address.line2 ? `, ${order.address.line2}` : ''}, {order.address.city} {order.address.postcode}, {order.address.country}
        </div>
        {order.deliveryFrom && (
          <div className="muted">{order.deliveryLabel}{order.deliveryOption === 'EXPRESS' ? ' (dispatch promptly)' : ''} · promised {deliveryRange(order.deliveryFrom, order.deliveryTo)}</div>
        )}
        <TrackingInfo order={order} />
        {base === 'seller' && <div><Link to={`/seller/messages/new?order=${order.id}`} className="message-seller">Message {order.customerName}</Link></div>}
        <details className="activity-toggle">
          <summary>Order activity</summary>
          <OrderActivity order={order} />
        </details>
        {next === 'SHIPPED' && (
          <div className="ship-form">
            <div className="form-field">
              <label className="field-label small" htmlFor={`carrier-${order.id}`}>Carrier (optional)</label>
              <input id={`carrier-${order.id}`} className="rounded-input" list="carrier-options" value={carrier} onChange={(e) => setCarrier(e.target.value)} placeholder="e.g. Royal Mail" maxLength={60} autoComplete="off" />
            </div>
            <div className="form-field">
              <label className="field-label small" htmlFor={`tracking-${order.id}`}>Tracking number (optional)</label>
              <input id={`tracking-${order.id}`} className="rounded-input" value={tracking} onChange={(e) => setTracking(e.target.value)} placeholder="So the customer can follow the parcel" maxLength={80} autoComplete="off" />
            </div>
            <datalist id="carrier-options">{CARRIERS.map((c) => <option key={c} value={c} />)}</datalist>
          </div>
        )}
        <div className="row-wrap">
          {order.nextStatuses.length === 0 ? <span className="muted">No further changes possible.</span> : (
            <>
              <select className="pill-select compact" value={next} onChange={(e) => setNext(e.target.value as OrderStatus | '')} aria-label={`New status for order ${order.id}`}>
                <option value="">Change status…</option>
                {order.nextStatuses.map((s) => <option key={s} value={s}>{statusLabel(s)}</option>)}
              </select>
              <button className="submit-btn" disabled={!next || busy} onClick={apply}>{busy ? 'Saving…' : 'Apply'}</button>
            </>
          )}
        </div>
      </div>
    </details>
  );
}

/** The order list used by both admins (all orders) and sellers (only their own). */
export function OrdersManager({ base }: { base: 'admin' | 'seller' }) {
  const [params, setParams] = useSearchParams();
  const status = params.get('status') ?? '';
  const page = Number(params.get('page') ?? '0') || 0;
  const { data, error, loading, setData } = useAsync(() => api<Page<Order>>(`/${base}/orders`, { query: { status, page, size: 15 } }), [status, page, base]);

  function update(next: Record<string, string>) {
    const p = new URLSearchParams(params);
    Object.entries(next).forEach(([k, v]) => (v ? p.set(k, v) : p.delete(k)));
    if (!('page' in next)) p.delete('page');
    setParams(p);
  }

  return (
    <div className="page">
      <div>
        <h1 className="page-title">Orders</h1>
        {data && <p className="page-subtitle">{data.totalItems} order{data.totalItems === 1 ? '' : 's'}</p>}
      </div>
      <div className="row-wrap" role="group" aria-label="Filter by status">
        <button className={`chip ${!status ? 'active' : ''}`} onClick={() => update({ status: '' })}>All</button>
        {STATUSES.map((s) => <button key={s} className={`chip ${status === s ? 'active' : ''}`} onClick={() => update({ status: s })}>{statusLabel(s)}</button>)}
      </div>
      {error && <div className="notice error">{error}</div>}
      {loading && !data ? <div className="loading">Loading…</div> : data && data.items.length === 0 ? <div className="empty">No orders here.</div> : (
        <div className="stack">
          {data?.items.map((o) => (
            <OrderRow key={o.id} base={base} order={o} onChanged={(u) => setData((prev) => prev && { ...prev, items: prev.items.map((x) => (x.id === u.id ? u : x)) })} />
          ))}
        </div>
      )}
      {data && <Pagination page={data.page} totalPages={data.totalPages} onChange={(p) => update({ page: String(p) })} />}
    </div>
  );
}

export const AdminOrders = () => <OrdersManager base="admin" />;
