import { useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api, ApiError } from '../../api/client';
import type { Page, SentEmail, User } from '../../api/types';
import { Pagination } from '../../components/Pagination';
import { dateTime } from '../../ui/format';
import { useAsync } from '../../ui/useAsync';

/** Every email the shop sent. With no mail server configured they are recorded here but never delivered. */
export function AdminEmails() {
  const [params, setParams] = useSearchParams();
  const page = Number(params.get('page') ?? '0') || 0;
  const { data, error, loading } = useAsync(() => api<Page<SentEmail>>('/admin/emails', { query: { page, size: 20 } }), [page]);
  const anyLogged = data?.items.some((e) => e.status === 'LOGGED');

  return (
    <div className="page">
      <div>
        <h1 className="page-title">Emails</h1>
        {data && <p className="page-subtitle">{data.totalItems} email{data.totalItems === 1 ? '' : 's'}, newest first</p>}
      </div>
      {anyLogged && (
        <div className="notice" role="status">
          No mail server is set up, so these emails were recorded but <b>not delivered</b>. Set <code>MAIL_HOST</code> (and the other <code>MAIL_*</code> settings) to send them for real.
        </div>
      )}
      <ConfirmCustomerEmail />
      {error && <div className="notice error">{error}</div>}
      {loading && !data ? <div className="loading">Loading…</div> : data && data.items.length === 0 ? <div className="empty">No emails yet.</div> : (
        <div className="stack">
          {data?.items.map((e) => (
            <details key={e.id} className="square-review-box static">
              <summary className="row-wrap" style={{ cursor: 'pointer', listStyle: 'none' }}>
                <b>{e.subject}</b>
                <span className="muted">to {e.to}</span>
                <span className="spacer" />
                <span className="muted">{dateTime(e.createdAt)}</span>
                <span className={`status-pill mail-${e.status}`}>{e.status === 'LOGGED' ? 'Not sent' : e.status === 'SENT' ? 'Sent' : 'Failed'}</span>
              </summary>
              {e.error && <div className="notice error" style={{ marginTop: 12 }}>{e.error}</div>}
              <pre className="mail-body">{e.body}</pre>
            </details>
          ))}
        </div>
      )}
      {data && <Pagination page={data.page} totalPages={data.totalPages} onChange={(p) => setParams(p ? { page: String(p) } : {})} />}
    </div>
  );
}

/** Support: confirm a customer's address by hand when their confirmation email never arrived. */
function ConfirmCustomerEmail() {
  const [email, setEmail] = useState('');
  const [result, setResult] = useState<{ ok: boolean; text: string } | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setResult(null);
    try {
      const user = await api<User>('/admin/customers/verify-email', { method: 'POST', body: { email: email.trim() } });
      setResult({ ok: true, text: `${user.name}'s email (${user.email}) is confirmed.` });
      setEmail('');
    } catch (err) {
      setResult({ ok: false, text: err instanceof ApiError ? err.message : 'Could not confirm that email.' });
    } finally {
      setBusy(false);
    }
  }

  return (
    <details className="square-review-box static">
      <summary style={{ cursor: 'pointer' }}><b>Confirm a customer's email by hand</b> <span className="muted">(if their confirmation email never arrived)</span></summary>
      <form className="row-wrap" style={{ marginTop: 12 }} onSubmit={submit}>
        <input className="rounded-input" type="email" placeholder="customer@example.com" value={email} onChange={(e) => setEmail(e.target.value)}
          required maxLength={190} aria-label="Customer's email" style={{ flex: '1 1 240px' }} />
        <button className="submit-btn" disabled={busy || !email.trim()}>{busy ? 'Confirming…' : 'Confirm email'}</button>
      </form>
      {result && <div className={`notice ${result.ok ? 'ok' : 'error'}`} role={result.ok ? 'status' : 'alert'} style={{ marginTop: 12 }}>{result.text}</div>}
    </details>
  );
}
