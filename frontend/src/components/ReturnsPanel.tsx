import { useState, type FormEvent } from 'react';
import { api } from '../api/client';
import type { Order, ReturnRequest } from '../api/types';
import { dateOnly, money } from '../ui/format';
import { RETURN_REASONS, RETURN_STATUS_LABEL } from '../ui/returns';
import { useToast } from '../ui/Toast';

export function ReturnStatusPill({ status }: { status: ReturnRequest['status'] }) {
  return <span className={`status-pill status-${status}`}>{RETURN_STATUS_LABEL[status]}</span>;
}

/** One return, as the customer sees it. */
function ReturnCard({ r, onWithdraw, busy }: { r: ReturnRequest; onWithdraw: () => void; busy: boolean }) {
  return (
    <div className="return-card">
      <div className="row-wrap">
        <b>Return #{r.id}</b>
        <span className="muted">{dateOnly(r.createdAt)}</span>
        <span className="spacer" />
        <ReturnStatusPill status={r.status} />
      </div>
      {r.items.map((i) => <div key={i.orderItemId} className="row"><span>{i.quantity} × {i.productName}</span><span className="spacer" /><span>{money(i.lineTotal)}</span></div>)}
      <div className="muted">Reason: {r.reasonLabel}{r.comment ? ` · “${r.comment}”` : ''}</div>
      {r.status === 'REQUESTED' && <p className="return-note">The seller will look at your request and get back to you by email.</p>}
      {r.status === 'APPROVED' && <p className="return-note ok"><b>Approved.</b> {r.sellerNote ?? 'Please send the items back to the seller.'} You'll be refunded once the seller has received them.</p>}
      {r.status === 'REJECTED' && <p className="return-note bad"><b>Declined.</b> {r.sellerNote}</p>}
      {r.status === 'REFUNDED' && r.refundAmount != null && (
        <p className="return-note ok">
          <b>{money(r.refundAmount)} refunded</b>{' '}
          {r.paymentMethod === 'CARD' ? 'to your card. It can take 5 to 10 working days to show on your statement.' : `— you paid on delivery, so ${r.sellerName} refunds you directly.`}
          {r.sellerNote ? ` ${r.sellerNote}` : ''}
        </p>
      )}
      {r.status === 'REQUESTED' && <div><button className="side-btn" onClick={onWithdraw} disabled={busy}>Withdraw request</button></div>}
    </div>
  );
}

/** Returns and refunds on the customer's order page: ask to send items back, and follow what happens next. */
export function ReturnsPanel({ order, onChange }: { order: Order; onChange: (o: Order) => void }) {
  const toast = useToast();
  const [open, setOpen] = useState(false);
  const [qty, setQty] = useState<Record<number, number>>({});
  const [reason, setReason] = useState('DAMAGED');
  const [comment, setComment] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  if (order.status !== 'DELIVERED' && order.returns.length === 0) return null;

  const chosen = Object.values(qty).reduce((a, b) => a + b, 0);
  const windowClosed = order.status === 'DELIVERED' && !order.canReturn && order.returnDeadline !== null && new Date(order.returnDeadline) < new Date();
  const nothingLeft = order.status === 'DELIVERED' && !order.canReturn && !windowClosed;

  async function reload() {
    onChange(await api<Order>(`/orders/${order.id}`));
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError('');
    setBusy(true);
    try {
      const items = Object.entries(qty).filter(([, n]) => n > 0).map(([orderItemId, quantity]) => ({ orderItemId: Number(orderItemId), quantity }));
      await api(`/orders/${order.id}/returns`, { method: 'POST', body: { items, reason, comment: comment.trim() || null } });
      toast.show('Return requested. The seller will be in touch.');
      setOpen(false);
      setQty({});
      setComment('');
      await reload();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not send your return request.');
    } finally {
      setBusy(false);
    }
  }

  async function withdraw(r: ReturnRequest) {
    if (!window.confirm('Withdraw this return request?')) return;
    setBusy(true);
    try {
      await api(`/orders/${order.id}/returns/${r.id}/cancel`, { method: 'POST' });
      toast.show('Return request withdrawn');
      await reload();
    } catch (err) {
      toast.show(err instanceof Error ? err.message : 'Could not withdraw the request.', 'error');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="order-card">
      <div className="order-body single">
        <div className="order-main">
          <h2 className="order-status">Returns &amp; refunds</h2>
          {order.returns.map((r) => <ReturnCard key={r.id} r={r} busy={busy} onWithdraw={() => withdraw(r)} />)}

          {order.canReturn && !open && (
            <div className="row-wrap">
              <button className="side-btn" onClick={() => setOpen(true)}>Return items</button>
              {order.returnDeadline && <span className="muted">You can return items until {dateOnly(order.returnDeadline)}.</span>}
            </div>
          )}
          {windowClosed && <p className="muted">The return window for this order has closed.</p>}
          {nothingLeft && order.returns.length > 0 && <p className="muted">Everything from this order has been returned or is being returned.</p>}

          {order.canReturn && open && (
            <form className="stack return-form" onSubmit={submit}>
              <h3 style={{ margin: 0 }}>Which items are you returning?</h3>
              {order.items.filter((i) => i.returnableQuantity > 0).map((i) => (
                <div key={i.id} className="row-wrap">
                  <label htmlFor={`rq-${i.id}`}>{i.productName} <span className="muted">({money(i.unitPrice)} each)</span></label>
                  <span className="spacer" />
                  <select id={`rq-${i.id}`} className="pill-select compact" value={qty[i.id] ?? 0} onChange={(e) => setQty({ ...qty, [i.id]: Number(e.target.value) })} aria-label={`How many ${i.productName} to return`}>
                    {Array.from({ length: i.returnableQuantity + 1 }, (_, n) => <option key={n} value={n}>{n === 0 ? 'None' : n}</option>)}
                  </select>
                </div>
              ))}
              <div className="form-field">
                <label className="field-label small" htmlFor="return-reason">Why are you returning {chosen === 1 ? 'it' : 'them'}?</label>
                <select id="return-reason" className="pill-select" value={reason} onChange={(e) => setReason(e.target.value)}>
                  {RETURN_REASONS.map(([v, label]) => <option key={v} value={v}>{label}</option>)}
                </select>
              </div>
              <div className="form-field">
                <label className="field-label small" htmlFor="return-comment">Anything the seller should know? (optional)</label>
                <textarea id="return-comment" className="rounded-input" value={comment} onChange={(e) => setComment(e.target.value)} maxLength={500} />
              </div>
              {error && <div className="notice error" role="alert">{error}</div>}
              <div className="row-wrap">
                <button className="submit-btn" disabled={busy || chosen === 0}>{busy ? 'Sending…' : 'Send return request'}</button>
                <button type="button" className="link-plain" onClick={() => setOpen(false)}>Cancel</button>
              </div>
            </form>
          )}
        </div>
      </div>
    </div>
  );
}
