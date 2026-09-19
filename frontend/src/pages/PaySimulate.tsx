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

  if (error && !data) return <div className="pay-wrap"><div className="pay-card"><div className="pay-icon off" aria-hidden="true">!</div><h1>We couldn't load this payment</h1><p>{error}</p><Link to="/orders" className="cart-btn big link-btn">My orders</Link></div></div>;
  if (loading && !data) return <div className="loading">Loading…</div>;
  if (!data) return null;
  if (!data.simulator) return <Navigate to={`/pay/return?ref=${ref}`} replace />;
  if (data.status !== 'PENDING') return <Navigate to={`/pay/return?ref=${ref}`} replace />;

  return (
    <div className="pay-wrap">
      <div className="pay-card">
        <div className="test-banner" role="note">TEST MODE. This stands in for the card payment page. No card is charged and no real money moves.</div>
        <h1>Test payment</h1>
        <div className="pay-amount"><span>Amount</span><b>{money(data.amount)}</b></div>
        <p>Choose what should happen, as if you had completed or abandoned the payment on the provider's page.</p>
        <div className="pay-actions">
          <button className="cart-btn big" onClick={() => void finish('PAID')} disabled={busy}>Simulate successful payment</button>
          <button className="side-btn" onClick={() => void finish('CANCELLED')} disabled={busy}>Simulate cancelling</button>
          <Link className="link-plain" to={`/pay/return?ref=${ref}&cancelled=1`}>Leave without paying</Link>
        </div>
      </div>
    </div>
  );
}
