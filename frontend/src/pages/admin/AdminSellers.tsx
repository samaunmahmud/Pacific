import { useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../../api/client';
import type { AdminSeller, LedgerEntry, Page, SellerStatus } from '../../api/types';
import { Pagination } from '../../components/Pagination';
import { Stars } from '../../components/Stars';
import { dateOnly, dateTime, money } from '../../ui/format';
import { useToast } from '../../ui/Toast';
import { useAsync } from '../../ui/useAsync';
import { StatusPill } from '../Orders';

const STATUSES: SellerStatus[] = ['PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED'];
const cap = (s: string) => s.charAt(0) + s.slice(1).toLowerCase();

function DefaultCommission() {
  const toast = useToast();
  const { data, reload } = useAsync(() => api<{ percent: number }>('/admin/settings/commission'), []);
  const [value, setValue] = useState('');

  async function save(e: FormEvent) {
    e.preventDefault();
    try {
      await api('/admin/settings/commission', { method: 'PUT', body: { percent: value } });
      toast.show('Default commission updated. It applies to orders placed from now on.');
      setValue('');
      reload();
    } catch (err) {
      toast.show(err instanceof Error ? err.message : 'Could not update the commission.', 'error');
    }
  }

  return (
    <form className="square-review-box static row-wrap" onSubmit={save}>
      <div>
        <div style={{ fontWeight: 'bold' }}>Default commission</div>
        <div className="muted">Taken from the item subtotal of every delivered seller order.</div>
      </div>
      <span className="spacer" />
      <span className="kpi"><span className="value">{data ? `${data.percent}%` : '…'}</span></span>
      <input className="num-input" style={{ width: 90 }} inputMode="decimal" placeholder="e.g. 12.5" value={value} onChange={(e) => setValue(e.target.value)} aria-label="New default commission percent" />
      <button className="submit-btn" disabled={!value.trim()}>Update</button>
    </form>
  );
}

function Ledger({ sellerId }: { sellerId: number }) {
  const [page, setPage] = useState(0);
  const { data, error } = useAsync(() => api<Page<LedgerEntry>>(`/admin/sellers/${sellerId}/ledger`, { query: { page, size: 10 } }), [sellerId, page]);
  if (error) return <div className="notice error">{error}</div>;
  if (!data) return <div className="muted">Loading…</div>;
  if (data.items.length === 0) return <div className="muted">No ledger entries yet.</div>;
  return (
    <div className="stack" style={{ gap: 8 }}>
      <div className="table-wrap">
        <table className="data">
          <thead><tr><th>When</th><th>Type</th><th>Note</th><th style={{ textAlign: 'right' }}>Amount</th></tr></thead>
          <tbody>
            {data.items.map((e) => (
              <tr key={e.id}><td>{dateTime(e.createdAt)}</td><td>{cap(e.type)}</td><td>{e.note ?? ''}</td><td style={{ textAlign: 'right', color: e.amount < 0 ? 'var(--danger)' : 'inherit' }}>{money(e.amount)}</td></tr>
            ))}
          </tbody>
        </table>
      </div>
      <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />
    </div>
  );
}

function SellerCard({ row, onChanged }: { row: AdminSeller; onChanged: (r: AdminSeller) => void }) {
  const toast = useToast();
  const s = row.seller;
  const [commission, setCommission] = useState('');
  const [amount, setAmount] = useState('');
  const [note, setNote] = useState('');
  const [showLedger, setShowLedger] = useState(false);
  const [ledgerKey, setLedgerKey] = useState(0);

  async function run(fn: () => Promise<AdminSeller | unknown>, done: string) {
    try {
      const result = await fn();
      if (result && typeof result === 'object' && 'seller' in result) onChanged(result as AdminSeller);
      toast.show(done);
      return true;
    } catch (err) {
      toast.show(err instanceof Error ? err.message : 'Action failed.', 'error');
      return false;
    }
  }

  const setStatus = (status: SellerStatus, why?: string) => {
    const reason = why ? window.prompt(why) : '';
    if (reason === null) return; // cancelled
    void run(() => api<AdminSeller>(`/admin/sellers/${s.id}/status`, { method: 'PATCH', body: { status, note: reason || null } }), `${s.storeName} is now ${status.toLowerCase()}`);
  };

  async function payout(e: FormEvent) {
    e.preventDefault();
    const ok = await run(async () => {
      await api(`/admin/sellers/${s.id}/payouts`, { method: 'POST', body: { amount, note: note || null } });
      return api<AdminSeller>(`/admin/sellers/${s.id}`);
    }, 'Payout recorded');
    if (ok) { setAmount(''); setNote(''); setLedgerKey((k) => k + 1); }
  }

  return (
    <article className="square-review-box static stack">
      <div className="row-wrap">
        <div>
          <div style={{ fontWeight: 'bold', fontSize: 18 }}>{s.storeName}</div>
          <div className="muted">{row.ownerName}{row.ownerEmail ? ` · ${row.ownerEmail}` : ''} · applied {dateOnly(s.createdAt)}</div>
        </div>
        <span className="spacer" />
        <StatusPill status={s.status} />
      </div>
      {s.description && <p style={{ margin: 0, color: '#555' }}>{s.description}</p>}
      {s.statusNote && <div className="muted">Note: {s.statusNote}</div>}

      <div className="row-wrap" style={{ gap: 24 }}>
        <span><b>{row.productCount}</b> products</span>
        <span><Stars value={s.ratingAvg} count={s.ratingCount} /></span>
        <span>Balance owed: <b>{money(row.balance)}</b></span>
        <span>Commission: <b>{s.commissionPercent}%</b>{s.commissionOverridden ? ' (custom)' : ' (default)'}</span>
        {s.status === 'APPROVED' && <Link to={`/sellers/${s.slug}`} className="view-all-link">View storefront</Link>}
      </div>

      <div className="row-wrap">
        {(s.status === 'PENDING' || s.status === 'REJECTED') && <button className="submit-btn" onClick={() => setStatus('APPROVED')}>Approve</button>}
        {s.status === 'PENDING' && <button className="ghost-btn" onClick={() => setStatus('REJECTED', 'Reason for rejecting (shown to the applicant):')}>Reject</button>}
        {s.status === 'APPROVED' && <button className="ghost-btn" onClick={() => setStatus('SUSPENDED', 'Reason for suspending (shown to the seller):')}>Suspend</button>}
        {s.status === 'SUSPENDED' && <button className="submit-btn" onClick={() => setStatus('APPROVED')}>Reinstate</button>}
      </div>

      {(s.status === 'APPROVED' || s.status === 'SUSPENDED') && (
        <>
          <hr />
          <div className="row-wrap">
            <input className="num-input" style={{ width: 90 }} inputMode="decimal" placeholder="% custom" value={commission} onChange={(e) => setCommission(e.target.value)} aria-label={`Custom commission for ${s.storeName}`} />
            <button className="ghost-btn" disabled={!commission.trim()} onClick={() => run(() => api<AdminSeller>(`/admin/sellers/${s.id}/commission`, { method: 'PUT', body: { percent: commission } }), 'Custom commission set').then((ok) => ok && setCommission(''))}>Set custom rate</button>
            {s.commissionOverridden && <button className="ghost-btn" onClick={() => run(() => api<AdminSeller>(`/admin/sellers/${s.id}/commission`, { method: 'PUT', body: { percent: null } }), 'Back to the default rate')}>Use default</button>}
            <span className="spacer" />
            <form className="row-wrap" onSubmit={payout}>
              <input className="num-input" style={{ width: 100 }} inputMode="decimal" placeholder="£ amount" value={amount} onChange={(e) => setAmount(e.target.value)} aria-label={`Payout amount for ${s.storeName}`} />
              <input className="rounded-input" style={{ width: 180 }} placeholder="Reference (optional)" value={note} onChange={(e) => setNote(e.target.value)} maxLength={200} aria-label="Payout reference" />
              <button className="submit-btn" disabled={!amount.trim() || row.balance <= 0}>Record payout</button>
            </form>
          </div>
          <div><button className="view-all-link" onClick={() => setShowLedger((v) => !v)}>{showLedger ? 'Hide ledger' : 'Show ledger'}</button></div>
          {showLedger && <Ledger key={ledgerKey} sellerId={s.id} />}
        </>
      )}
    </article>
  );
}

export function AdminSellers() {
  const [params, setParams] = useSearchParams();
  const status = params.get('status') ?? '';
  const page = Number(params.get('page') ?? '0') || 0;
  const { data, error, loading, setData } = useAsync(() => api<Page<AdminSeller>>('/admin/sellers', { query: { status, page, size: 10 } }), [status, page]);

  function update(next: Record<string, string>) {
    const p = new URLSearchParams(params);
    Object.entries(next).forEach(([k, v]) => (v ? p.set(k, v) : p.delete(k)));
    if (!('page' in next)) p.delete('page');
    setParams(p);
  }

  return (
    <div className="page">
      <div>
        <h1 className="page-title">Sellers</h1>
        {data && <p className="page-subtitle">{data.totalItems} seller{data.totalItems === 1 ? '' : 's'}</p>}
      </div>
      <DefaultCommission />
      <div className="row-wrap" role="group" aria-label="Filter by status">
        <button className={`chip ${!status ? 'active' : ''}`} onClick={() => update({ status: '' })}>All</button>
        {STATUSES.map((s) => <button key={s} className={`chip ${status === s ? 'active' : ''}`} onClick={() => update({ status: s })}>{cap(s)}</button>)}
      </div>
      {error && <div className="notice error">{error}</div>}
      {loading && !data ? <div className="loading">Loading…</div> : data && data.items.length === 0 ? <div className="empty">No sellers here.</div> : (
        <div className="stack">
          {data?.items.map((row) => (
            <SellerCard key={row.seller.id} row={row} onChanged={(r) => setData((prev) => prev && { ...prev, items: prev.items.map((x) => (x.seller.id === r.seller.id ? r : x)) })} />
          ))}
        </div>
      )}
      {data && <Pagination page={data.page} totalPages={data.totalPages} onChange={(p) => update({ page: String(p) })} />}
    </div>
  );
}
