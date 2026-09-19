import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../api/client';
import type { Payment } from '../api/types';
import { useCart } from '../cart/CartContext';
import { minutesLeft, money } from '../ui/format';
import { useToast } from '../ui/Toast';
import { useAsync } from '../ui/useAsync';

const POLL_MS = 3000;
const MAX_POLLS = 10;

/**
 * Where the customer lands after the payment page (success, or "back" without paying). The server checks with the
 * payment provider each time we ask, so the result shows up even if the provider's webhook hasn't reached us yet.
 */
export function PayReturn() {
  const [params] = useSearchParams();
  const ref = params.get('ref');
  const cameBack = params.get('cancelled') === '1';
  const toast = useToast();
  const { refresh } = useCart();
  const { data, error, loading, reload } = useAsync(() => api<Payment>(`/payments/${ref}`), [ref]);
  const [busy, setBusy] = useState(false);
  const polls = useRef(0);

  // Right after paying, the confirmation can lag a few seconds: keep asking for a little while.
  useEffect(() => {
    if (!data || data.status !== 'PENDING' || cameBack || polls.current >= MAX_POLLS) return;
    const timer = window.setTimeout(() => {
      polls.current += 1;
      reload();
    }, POLL_MS);
    return () => window.clearTimeout(timer);
  }, [data, cameBack, reload]);

  async function cancel() {
    if (!window.confirm('Cancel this payment? Your order will be cancelled and nothing will be charged.')) return;
    setBusy(true);
    try {
      await api<Payment>(`/payments/${ref}/cancel`, { method: 'POST' });
      toast.show('Payment cancelled');
    } catch (e) {
      toast.show(e instanceof Error ? e.message : 'Could not cancel the payment.', 'error');
    } finally {
      setBusy(false);
      reload();
      void refresh();
    }
  }

  if (!ref) return <div className="page page-narrow"><div className="notice error">This payment link is incomplete.</div><Link to="/orders" className="submit-btn" style={{ alignSelf: 'flex-start' }}>My orders</Link></div>;
  if (error && !data) return <div className="page page-narrow"><div className="notice error">{error}</div><Link to="/orders" className="submit-btn" style={{ alignSelf: 'flex-start' }}>My orders</Link></div>;
  if (!data) return <div className="loading">{loading ? 'Confirming your payment…' : 'Loading…'}</div>;

  const p = data;
  const refunded = p.refundedAmount > 0 && p.refundedAmount >= p.amount;

  return (
    <div className="page page-narrow">
      <h1 className="page-title">Payment</h1>

      {p.status === 'PAID' && !refunded && (
        <div className="stack">
          <div className="notice ok" role="status">Payment received. Thank you! Your order is confirmed and the seller will start preparing it.</div>
          <div className="square-review-box static"><div className="line"><span>Paid by card</span><b>{money(p.amount)}</b></div></div>
          <Link to="/orders" className="submit-btn" style={{ alignSelf: 'flex-start' }}>View my orders</Link>
        </div>
      )}

      {p.status === 'PAID' && refunded && (
        <div className="stack">
          <div className="notice" role="status">This payment arrived after the checkout had been released, so it was refunded in full ({money(p.refundedAmount)}). Your items weren't kept for you. Please place a new order.</div>
          <Link to="/cart" className="submit-btn" style={{ alignSelf: 'flex-start' }}>Back to cart</Link>
        </div>
      )}

      {p.status === 'PENDING' && (
        <div className="stack">
          {cameBack || polls.current >= MAX_POLLS ? (
            <div className="notice" role="status">We haven't received your payment yet. Your items are reserved for about {minutesLeft(p.expiresAt)} more minute{minutesLeft(p.expiresAt) === 1 ? '' : 's'}.</div>
          ) : (
            <div className="notice" role="status">Confirming your payment…</div>
          )}
          <div className="square-review-box static"><div className="line grand"><span>Total to pay</span><span>{money(p.amount)}</span></div></div>
          <div className="row-wrap">
            {p.checkoutUrl && <button className="submit-btn" onClick={() => window.location.assign(p.checkoutUrl!)} disabled={busy}>Pay now</button>}
            <button className="ghost-btn" onClick={reload} disabled={busy}>I've already paid — check again</button>
            <button className="ghost-btn" onClick={cancel} disabled={busy}>Cancel payment</button>
          </div>
        </div>
      )}

      {(p.status === 'CANCELLED' || p.status === 'EXPIRED') && (
        <div className="stack">
          <div className="notice" role="status">
            {p.status === 'EXPIRED' ? 'This payment timed out, so the order was cancelled.' : 'Payment cancelled.'} Nothing was charged and your items were released.
          </div>
          <div className="row-wrap">
            <Link to="/cart" className="submit-btn">Back to cart</Link>
            <Link to="/orders" className="ghost-btn">My orders</Link>
          </div>
        </div>
      )}
    </div>
  );
}
