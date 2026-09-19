import { useState } from 'react';
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom';
import { api } from '../api/client';
import type { Payment } from '../api/types';
import { money } from '../ui/format';
import { useToast } from '../ui/Toast';
import { useAsync } from '../ui/useAsync';

/**
 * Stand-in for the card provider's hosted page, used only when the backend runs with the payment simulator on
 * (local development). It has no card form on purpose: it can never receive card details or move money.
 */
export function PaySimulate() {
  const { ref } = useParams();
  const navigate = useNavigate();
  const toast = useToast();
  const { data, error, loading } = useAsync(() => api<Payment>(`/payments/${ref}`), [ref]);
  const [busy, setBusy] = useState(false);

  async function finish(outcome: 'PAID' | 'CANCELLED') {
    setBusy(true);
    try {
      await api<Payment>(`/payments/${ref}/simulate`, { method: 'POST', body: { outcome } });
      navigate(`/pay/return?ref=${ref}${outcome === 'CANCELLED' ? '&cancelled=1' : ''}`, { replace: true });
    } catch (e) {
      toast.show(e instanceof Error ? e.message : 'Could not complete the test payment.', 'error');
      setBusy(false);
    }
  }

  if (error && !data) return <div className="page page-narrow"><div className="notice error">{error}</div></div>;
  if (loading && !data) return <div className="loading">Loading…</div>;
  if (!data) return null;
  if (!data.simulator) return <Navigate to={`/pay/return?ref=${ref}`} replace />;
  if (data.status !== 'PENDING') return <Navigate to={`/pay/return?ref=${ref}`} replace />;

  return (
    <div className="page page-narrow">
      <div className="test-banner" role="note">TEST MODE. This is a stand-in for the card payment page. No card is charged and no real money moves.</div>
      <h1 className="page-title">Test payment</h1>
      <div className="square-review-box static stack">
        <div className="line grand"><span>Amount</span><span>{money(data.amount)}</span></div>
        <p className="muted" style={{ margin: 0 }}>Choose what should happen, as if you had completed or abandoned the payment on the provider's page.</p>
        <div className="row-wrap">
          <button className="submit-btn" onClick={() => void finish('PAID')} disabled={busy}>Simulate successful payment</button>
          <button className="ghost-btn" onClick={() => void finish('CANCELLED')} disabled={busy}>Simulate cancelling</button>
          <Link className="ghost-btn" to={`/pay/return?ref=${ref}&cancelled=1`}>Leave without paying</Link>
        </div>
      </div>
    </div>
  );
}
