import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../../api/client';
import type { Order, OrderStatus, Page } from '../../api/types';
import { Pagination } from '../../components/Pagination';
import { dateTime, money } from '../../ui/format';
import { useToast } from '../../ui/Toast';
import { useAsync } from '../../ui/useAsync';
import { StatusPill } from '../Orders';

const STATUSES: OrderStatus[] = ['PLACED', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED'];

function OrderRow({ order, onChanged, base }: { order: Order; onChanged: (o: Order) => void; base: 'admin' | 'seller' }) {
  const toast = useToast();
  const [next, setNext] = useState<OrderStatus | ''>('');
  const [busy, setBusy] = useState(false);

  async function apply() {
    if (!next) return;
    if (next === 'CANCELLED' && !window.confirm(`Cancel order #${order.id}? Stock will be returned.`)) return;
    setBusy(true);
    try {
      onChanged(await api<Order>(`/${base}/orders/${order.id}/status`, { method: 'PATCH', body: { status: next } }));
      setNext('');
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
        <div className="row-wrap">
          {order.nextStatuses.length === 0 ? <span className="muted">No further changes possible.</span> : (
            <>
              <select className="pill-select compact" value={next} onChange={(e) => setNext(e.target.value as OrderStatus | '')} aria-label={`New status for order ${order.id}`}>
                <option value="">Change status…</option>
                {order.nextStatuses.map((s) => <option key={s} value={s}>{s.charAt(0) + s.slice(1).toLowerCase()}</option>)}
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
        <h1 className="page-title serif">Orders</h1>
        {data && <p className="page-subtitle">{data.totalItems} order{data.totalItems === 1 ? '' : 's'}</p>}
      </div>
      <div className="row-wrap" role="group" aria-label="Filter by status">
        <button className={`chip ${!status ? 'active' : ''}`} onClick={() => update({ status: '' })}>All</button>
        {STATUSES.map((s) => <button key={s} className={`chip ${status === s ? 'active' : ''}`} onClick={() => update({ status: s })}>{s.charAt(0) + s.slice(1).toLowerCase()}</button>)}
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
