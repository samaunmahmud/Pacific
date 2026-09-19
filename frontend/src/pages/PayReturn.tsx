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

  if (!ref) return <div className="pay-wrap"><div className="pay-card"><div className="pay-icon off" aria-hidden="true">!</div><h1>This payment link is incomplete</h1><Link to="/orders" className="cart-btn big link-btn">My orders</Link></div></div>;
  if (error && !data) return <div className="pay-wrap"><div className="pay-card"><div className="pay-icon off" aria-hidden="true">!</div><h1>We couldn't load this payment</h1><p>{error}</p><Link to="/orders" className="cart-btn big link-btn">My orders</Link></div></div>;
  if (!data) return <div className="loading">{loading ? 'Confirming your payment…' : 'Loading…'}</div>;

  const p = data;
  const refunded = p.refundedAmount > 0 && p.refundedAmount >= p.amount;
  const waiting = p.status === 'PENDING' && !cameBack && polls.current < MAX_POLLS;

  return (
    <div className="pay-wrap">
      <div className="pay-card">
        {p.status === 'PAID' && !refunded && (
          <>
            <div className="pay-icon ok" aria-hidden="true">✓</div>
            <h1>Payment received</h1>
            <p>Thank you! Your order is confirmed and the seller will start preparing it.</p>
            <div className="pay-amount"><span>Paid by card</span><b>{money(p.amount)}</b></div>
            <Link to="/orders" className="cart-btn big link-btn">View my orders</Link>
          </>
        )}

        {p.status === 'PAID' && refunded && (
          <>
            <div className="pay-icon warn" aria-hidden="true">↩</div>
            <h1>Payment refunded</h1>
            <p>This payment arrived after the checkout had been released, so it was refunded in full ({money(p.refundedAmount)}). Your items weren't kept for you. Please place a new order.</p>
            <Link to="/cart" className="cart-btn big link-btn">Back to cart</Link>
          </>
        )}

        {p.status === 'PENDING' && (
          <>
            <div className={`pay-icon ${waiting ? 'wait' : 'warn'}`} aria-hidden="true">{waiting ? '…' : '!'}</div>
            <h1>{waiting ? 'Confirming your payment…' : 'Payment not completed yet'}</h1>
            <p role="status">
              {waiting
                ? "This usually takes a few seconds. Please don't close this page."
                : `We haven't received your payment. Your items are reserved for about ${minutesLeft(p.expiresAt)} more minute${minutesLeft(p.expiresAt) === 1 ? '' : 's'}.`}
            </p>
            <div className="pay-amount"><span>Total to pay</span><b>{money(p.amount)}</b></div>
            <div className="pay-actions">
              {p.checkoutUrl && <button className="cart-btn big" onClick={() => window.location.assign(p.checkoutUrl!)} disabled={busy}>Pay now</button>}
              <button className="side-btn" onClick={reload} disabled={busy}>I've already paid, check again</button>
              <button className="link-plain" onClick={cancel} disabled={busy}>Cancel payment</button>
            </div>
          </>
        )}

        {(p.status === 'CANCELLED' || p.status === 'EXPIRED') && (
          <>
            <div className="pay-icon off" aria-hidden="true">✕</div>
            <h1>{p.status === 'EXPIRED' ? 'Payment timed out' : 'Payment cancelled'}</h1>
            <p>{p.status === 'EXPIRED' ? 'The order was cancelled because it wasn\'t paid in time. ' : ''}Nothing was charged and your items were released.</p>
            <div className="pay-actions">
              <Link to="/cart" className="cart-btn big link-btn">Back to cart</Link>
              <Link to="/orders" className="side-btn">My orders</Link>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
