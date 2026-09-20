import { useState, type FormEvent } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import type { CheckoutResponse, PaymentConfig, PaymentMethod, SavedAddress } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { useCart } from '../cart/CartContext';
import { money } from '../ui/format';
import { useAsync } from '../ui/useAsync';

export function Checkout() {
  const { user } = useAuth();
  const { cart, refresh } = useCart();
  const navigate = useNavigate();
  const [form, setForm] = useState({ name: user?.name ?? '', line1: '', line2: '', city: '', postcode: '', country: 'United Kingdom' });
  const [method, setMethod] = useState<PaymentMethod>('PAY_ON_DELIVERY');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  // If this can't be loaded we simply don't offer card payment; pay on delivery always works.
  const { data: config } = useAsync(() => api<PaymentConfig>('/payments/config').catch((): PaymentConfig => ({ cardEnabled: false, simulator: false })), []);
  const cardEnabled = config?.cardEnabled ?? false;
  // Saved addresses (default first). If they can't be loaded the customer just types one.
  const { data: saved } = useAsync(() => api<SavedAddress[]>('/me/addresses').catch((): SavedAddress[] => []), []);
  const [choice, setChoice] = useState<number | 'new' | null>(null);
  const [saveIt, setSaveIt] = useState(true);
  const active = choice ?? (saved && saved.length > 0 ? saved[0].id : 'new');
  const picked = typeof active === 'number' ? saved?.find((a) => a.id === active) : undefined;
  const paying = cardEnabled && method === 'CARD';

  if (!cart) return <div className="loading">Loading…</div>;
  if (cart.items.length === 0 && !busy) return <Navigate to="/cart" replace />;

  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [k]: e.target.value });

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError('');
    setBusy(true);
    let leaving = false;
    try {
      const address = picked
        ? { name: picked.name, line1: picked.line1, line2: picked.line2, city: picked.city, postcode: picked.postcode, country: picked.country }
        : { ...form, line2: form.line2 || null };
      const result = await api<CheckoutResponse>('/orders', {
        method: 'POST',
        body: { ...address, paymentMethod: paying ? 'CARD' : 'PAY_ON_DELIVERY' },
      });
      // Remember a new address for next time. Best effort: the order is already placed, so this must never get in the way.
      const norm = (v: string | null | undefined) => (v ?? '').trim().toLowerCase();
      const alreadySaved = saved?.some((a) => norm(a.line1) === norm(address.line1) && norm(a.line2) === norm(address.line2)
        && norm(a.city) === norm(address.city) && norm(a.postcode) === norm(address.postcode) && norm(a.country) === norm(address.country));
      if (!picked && saveIt && !alreadySaved && (saved?.length ?? 0) < 10) {
        await api('/me/addresses', { method: 'POST', body: { ...address, makeDefault: (saved?.length ?? 0) === 0 } }).catch(() => undefined);
      }
      if (result.payment) {
        // Card: the customer pays on the provider's own page, then comes back to /pay/return.
        // Our servers never see card details.
        leaving = true;
        void refresh();
        if (result.payment.checkoutUrl) window.location.assign(result.payment.checkoutUrl);
        else navigate(`/pay/return?ref=${result.checkoutRef}`, { replace: true });
        return;
      }
      // Navigate first: refreshing the cart empties it, and an empty cart on this page redirects to /cart.
      // One seller -> straight to that order; several sellers -> the order list, which shows them together.
      if (result.orders.length === 1) navigate(`/orders/${result.orders[0].id}`, { replace: true, state: { placed: 1 } });
      else navigate('/orders', { replace: true, state: { placed: result.orders.length } });
      void refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not place your order.');
      void refresh(); // stock may have changed; show the latest cart
    } finally {
      if (!leaving) setBusy(false);
    }
  }

  return (
    <div className="page">
      <div className="row">
        <Link to="/cart" className="back-btn" aria-label="Back to cart">←</Link>
        <h1 className="page-title">Checkout</h1>
      </div>
      <form className="two-col" onSubmit={submit}>
        <div className="square-review-box static stack">
          <h2 style={{ margin: 0 }}>Delivery address</h2>
          {!saved ? <div className="loading">Loading your addresses…</div> : (
            <>
          {saved && saved.length > 0 && (
            <div className="pay-options" role="radiogroup" aria-label="Delivery address">
              {saved.map((a) => (
                <label key={a.id} className={`pay-option ${active === a.id ? 'selected' : ''}`}>
                  <input type="radio" name="address" checked={active === a.id} onChange={() => setChoice(a.id)} />
                  <span><b>{a.name}</b>{a.isDefault && <span className="status-pill status-APPROVED" style={{ marginLeft: 8 }}>Default</span>}<br />
                    <span className="muted">{a.line1}{a.line2 ? `, ${a.line2}` : ''}, {a.city} {a.postcode}, {a.country}</span></span>
                </label>
              ))}
              <label className={`pay-option ${active === 'new' ? 'selected' : ''}`}>
                <input type="radio" name="address" checked={active === 'new'} onChange={() => setChoice('new')} />
                <span><b>Use a different address</b></span>
              </label>
            </div>
          )}
          {active === 'new' && (
          <div className="form-grid">
            <div className="form-field full"><label className="field-label small" htmlFor="name">Full name</label><input id="name" className="rounded-input" value={form.name} onChange={set('name')} required maxLength={120} autoComplete="name" /></div>
            <div className="form-field full"><label className="field-label small" htmlFor="line1">Address line 1</label><input id="line1" className="rounded-input" value={form.line1} onChange={set('line1')} required maxLength={160} autoComplete="address-line1" /></div>
            <div className="form-field full"><label className="field-label small" htmlFor="line2">Address line 2 (optional)</label><input id="line2" className="rounded-input" value={form.line2} onChange={set('line2')} maxLength={160} autoComplete="address-line2" /></div>
            <div className="form-field"><label className="field-label small" htmlFor="city">Town / city</label><input id="city" className="rounded-input" value={form.city} onChange={set('city')} required maxLength={80} autoComplete="address-level2" /></div>
            <div className="form-field"><label className="field-label small" htmlFor="postcode">Postcode</label><input id="postcode" className="rounded-input" value={form.postcode} onChange={set('postcode')} required maxLength={20} autoComplete="postal-code" /></div>
            <div className="form-field full"><label className="field-label small" htmlFor="country">Country</label><input id="country" className="rounded-input" value={form.country} onChange={set('country')} required maxLength={80} autoComplete="country-name" /></div>
          </div>
          )}
          {active === 'new' && (saved?.length ?? 0) < 10 && (
            <label className="row" style={{ gap: 8 }}><input type="checkbox" checked={saveIt} onChange={(e) => setSaveIt(e.target.checked)} /> Save this address for next time</label>
          )}
          {saved && saved.length > 0 && <Link to="/account/addresses" className="muted" style={{ fontSize: 13 }}>Manage saved addresses</Link>}
            </>
          )}

          <h2 style={{ margin: '8px 0 0' }}>Payment</h2>
          {cardEnabled ? (
            <div className="pay-options" role="radiogroup" aria-label="Payment method">
              <label className={`pay-option ${method === 'PAY_ON_DELIVERY' ? 'selected' : ''}`}>
                <input type="radio" name="method" checked={method === 'PAY_ON_DELIVERY'} onChange={() => setMethod('PAY_ON_DELIVERY')} />
                <span><b>Pay on delivery</b><br /><span className="muted">Pay when your order arrives.</span></span>
              </label>
              <label className={`pay-option ${method === 'CARD' ? 'selected' : ''}`}>
                <input type="radio" name="method" checked={method === 'CARD'} onChange={() => setMethod('CARD')} />
                <span><b>Pay by card now</b><br /><span className="muted">You'll enter your card on a secure payment page. We never see your card details.</span></span>
              </label>
            </div>
          ) : (
            <div className="notice"><b>Payment:</b> pay on delivery.</div>
          )}
          {paying && config?.simulator && <div className="test-banner">Test mode: card payments are simulated. No real money is taken.</div>}
          {cart.shipments.length > 1 && <div className="notice">Your items come from {cart.shipments.length} sellers, so you'll get {cart.shipments.length} separate orders and deliveries{paying ? ', paid for in one card payment' : ''}.</div>}
        </div>

        <aside className="square-review-box static totals" aria-label="Order summary">
          <h2 style={{ margin: 0 }}>Order summary</h2>
          {cart.shipments.map((ship) => (
            <div key={ship.sellerName} className="stack" style={{ gap: 6 }}>
              <div className="muted" style={{ fontSize: 12 }}>Sold by {ship.sellerName}{cart.shipments.length > 1 ? ` · shipping ${ship.shipping === 0 ? 'FREE' : money(ship.shipping)}` : ''}</div>
              {cart.items.filter((i) => i.sellerName === ship.sellerName).map((i) => (
                <div key={i.productId} className="line"><span>{i.quantity} × {i.name}</span><span>{money(i.lineTotal)}</span></div>
              ))}
            </div>
          ))}
          <hr />
          <div className="line"><span>Subtotal</span><span>{money(cart.subtotal)}</span></div>
          <div className="line"><span>Shipping</span><span>{cart.shipping === 0 ? 'FREE' : money(cart.shipping)}</span></div>
          <div className="line grand"><span>Total</span><span>{money(cart.total)}</span></div>
          {error && <div className="notice error" role="alert">{error}</div>}
          <button className="submit-btn block" disabled={busy}>{busy ? (paying ? 'Taking you to payment…' : 'Placing order…') : paying ? 'Continue to payment' : 'Place order'}</button>
        </aside>
      </form>
    </div>
  );
}
