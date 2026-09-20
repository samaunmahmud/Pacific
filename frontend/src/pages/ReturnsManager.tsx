import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../api/client';
import type { Page, ReturnRequest } from '../api/types';
import { Pagination } from '../components/Pagination';
import { ReturnStatusPill } from '../components/ReturnsPanel';
import { dateTime, money } from '../ui/format';
import { useToast } from '../ui/Toast';
import { useAsync } from '../ui/useAsync';

const FILTERS: [string, string][] = [
  ['REQUESTED', 'Awaiting your decision'],
  ['APPROVED', 'Approved'],
  ['REFUNDED', 'Refunded'],
  ['REJECTED', 'Declined'],
  ['ALL', 'All'],
];

function ReturnRow({ r, base, onChanged }: { r: ReturnRequest; base: 'admin' | 'seller'; onChanged: (r: ReturnRequest) => void }) {
  const toast = useToast();
  const [note, setNote] = useState('');
  // what the seller typed; until they type, the box shows the most that may be refunded (known once approved)
  const [typed, setTyped] = useState<string | null>(null);
  const amount = typed ?? (r.maxRefund != null ? r.maxRefund.toFixed(2) : '');
  const [restock, setRestock] = useState(true);
  const [busy, setBusy] = useState(false);
  const card = r.paymentMethod === 'CARD';

  async function act(action: 'approve' | 'reject' | 'refund') {
    if (action === 'reject' && !note.trim()) { toast.show('Please tell the customer why you are declining.', 'error'); return; }
    const refundAmount = Number(amount);
    if (action === 'refund') {
      if (!(refundAmount > 0) || (r.maxRefund != null && refundAmount > r.maxRefund)) { toast.show(`Enter an amount up to ${money(r.maxRefund ?? 0)}.`, 'error'); return; }
      const where = card ? 'to the customer\'s card' : 'directly to the customer (they paid on delivery)';
      if (!window.confirm(`Refund ${money(refundAmount)} ${where}? This can't be undone.`)) return;
    }
    setBusy(true);
    try {
      const body = action === 'refund' ? { amount: refundAmount, restock, note: note.trim() || null } : { note: note.trim() || null };
      onChanged(await api<ReturnRequest>(`/${base}/returns/${r.id}/${action}`, { method: 'POST', body }));
      setNote('');
      toast.show(action === 'approve' ? 'Return approved' : action === 'reject' ? 'Return declined' : 'Refund issued');
    } catch (e) {
      toast.show(e instanceof Error ? e.message : 'That did not work.', 'error');
    } finally {
      setBusy(false);
    }
  }

  return (
    <article className="square-review-box static stack">
      <div className="row-wrap">
        <b>Return #{r.id}</b>
        <span>Order #{r.orderId}</span>
        <span>{r.customerName}</span>
        {base === 'admin' && <span className="muted">· {r.sellerName}</span>}
        <span className="muted">{dateTime(r.createdAt)}</span>
        <span className="spacer" />
        <ReturnStatusPill status={r.status} />
      </div>
      {r.items.map((i) => <div key={i.orderItemId} className="row"><span>{i.quantity} × {i.productName}</span><span className="spacer" /><span>{money(i.lineTotal)}</span></div>)}
      <div className="muted">Reason: <b>{r.reasonLabel}</b>{r.comment ? ` · “${r.comment}”` : ''}</div>

      {r.status === 'REQUESTED' && (
        <div className="stack return-form">
          <label className="field-label small" htmlFor={`note-${r.id}`}>Note to the customer</label>
          <textarea id={`note-${r.id}`} className="rounded-input" value={note} onChange={(e) => setNote(e.target.value)} maxLength={300}
            placeholder="If approving: the return address and any instructions. If declining: why (required)." />
          <div className="row-wrap">
            <button className="submit-btn" disabled={busy} onClick={() => act('approve')}>Approve return</button>
            <button className="side-btn" disabled={busy} onClick={() => act('reject')}>Decline</button>
          </div>
        </div>
      )}

      {r.status === 'APPROVED' && (
        <div className="stack return-form">
          <p className="muted" style={{ margin: 0 }}>
            {card ? 'Once the items have arrived, refund them. The money goes back to the customer\'s card.'
              : 'This was a pay on delivery order, so no money moves through Pacific: refund the customer yourself (cash or bank transfer), then confirm here to update your earnings.'}
          </p>
          <div className="ship-form">
            <div className="form-field">
              <label className="field-label small" htmlFor={`amt-${r.id}`}>Refund amount (most {money(r.maxRefund ?? 0)})</label>
              <input id={`amt-${r.id}`} className="rounded-input" inputMode="decimal" value={amount} onChange={(e) => setTyped(e.target.value)} />
            </div>
            <div className="form-field">
              <label className="field-label small" htmlFor={`rn-${r.id}`}>Note (optional)</label>
              <input id={`rn-${r.id}`} className="rounded-input" value={note} onChange={(e) => setNote(e.target.value)} maxLength={300} />
            </div>
          </div>
          <label className="row" style={{ gap: 8 }}><input type="checkbox" checked={restock} onChange={(e) => setRestock(e.target.checked)} /> Put the returned items back on sale</label>
          <div><button className="submit-btn" disabled={busy} onClick={() => act('refund')}>{busy ? 'Refunding…' : `Items received: refund ${money(Number(amount) || 0)}`}</button></div>
        </div>
      )}

      {r.status === 'REFUNDED' && r.refundAmount != null && (
        <p className="return-note ok">{money(r.refundAmount)} refunded {card ? 'to the card' : 'directly by the seller'} · {r.restocked ? 'items put back on sale' : 'items not put back on sale'}{r.sellerNote ? ` · ${r.sellerNote}` : ''}</p>
      )}
      {r.status === 'REJECTED' && <p className="return-note bad">Declined: {r.sellerNote}</p>}
      {r.status === 'APPROVED' && r.sellerNote && <p className="muted" style={{ margin: 0 }}>You told the customer: {r.sellerNote}</p>}
    </article>
  );
}

/** Return requests, for a seller (their own) or an admin (all): decide, then refund once the goods are back. */
export function ReturnsManager({ base }: { base: 'admin' | 'seller' }) {
  const [params, setParams] = useSearchParams();
  const status = params.get('status') ?? 'REQUESTED';
  const page = Number(params.get('page') ?? '0') || 0;
  const { data, error, loading, setData } = useAsync(
    () => api<Page<ReturnRequest>>(`/${base}/returns`, { query: { status: status === 'ALL' ? '' : status, page, size: 15 } }), [status, page, base]);

  function update(next: Record<string, string>) {
    const p = new URLSearchParams(params);
    Object.entries(next).forEach(([k, v]) => (v ? p.set(k, v) : p.delete(k)));
    if (!('page' in next)) p.delete('page');
    setParams(p);
  }

  return (
    <div className="page">
      <div>
        <h1 className="page-title">Returns</h1>
        {data && <p className="page-subtitle">{data.totalItems} return{data.totalItems === 1 ? '' : 's'}</p>}
      </div>
      <div className="row-wrap" role="group" aria-label="Filter by status">
        {FILTERS.map(([v, label]) => <button key={v} className={`chip ${status === v ? 'active' : ''}`} onClick={() => update({ status: v })}>{label}</button>)}
      </div>
      {error && <div className="notice error">{error}</div>}
      {loading && !data ? <div className="loading">Loading…</div> : data && data.items.length === 0 ? <div className="empty">No returns here.</div> : (
        <div className="stack">
          {data?.items.map((r) => (
            <ReturnRow key={r.id} r={r} base={base} onChanged={(u) => setData((prev) => prev && { ...prev, items: prev.items.map((x) => (x.id === u.id ? u : x)) })} />
          ))}
        </div>
      )}
      {data && <Pagination page={data.page} totalPages={data.totalPages} onChange={(p) => update({ page: String(p) })} />}
    </div>
  );
}
