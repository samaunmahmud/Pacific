import { useState, type FormEvent } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { api } from '../api/client';
import type { Seller } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { useSeller } from '../seller/SellerContext';
import { useToast } from '../ui/Toast';

const PERKS: [string, string][] = [
  ['🏪', 'Your own storefront — shoppers find you at “Sold by <your store>”.'],
  ['📦', 'You ship your own orders and track them from Seller Central.'],
  ['💷', 'Earnings are recorded when an order is delivered, minus the marketplace commission.'],
  ['⭐', 'Build a reputation: customers rate sellers as well as products.'],
];

function ApplyForm({ initial, onDone }: { initial?: Seller | null; onDone: () => void }) {
  const toast = useToast();
  const [storeName, setStoreName] = useState(initial?.storeName ?? '');
  const [description, setDescription] = useState(initial?.description ?? '');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      await api<Seller>('/seller/apply', { method: 'POST', body: { storeName: storeName.trim(), description: description.trim() || null } });
      toast.show('Application sent!');
      onDone();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not send your application.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="square-review-box static stack" onSubmit={submit}>
      <h2 style={{ margin: 0 }}>{initial ? 'Apply again' : 'Apply to sell'}</h2>
      <label className="field-label small" htmlFor="sn">Store name</label>
      <input id="sn" className="rounded-input" value={storeName} onChange={(e) => setStoreName(e.target.value)} required minLength={3} maxLength={80} />
      <label className="field-label small" htmlFor="sd">What will you sell?</label>
      <textarea id="sd" className="rounded-input" value={description} onChange={(e) => setDescription(e.target.value)} maxLength={1000} placeholder="A few lines for your storefront and for our review team" />
      {error && <div className="notice error" role="alert">{error}</div>}
      <div><button className="submit-btn" disabled={busy}>{busy ? 'Sending…' : 'Submit application'}</button></div>
    </form>
  );
}

/** "Sell on Pacific": onboarding and application status. Sellers are ordinary customer accounts. */
export function Sell() {
  const { user } = useAuth();
  const { seller, loading, refresh } = useSeller();
  const location = useLocation();

  return (
    <div className="page page-narrow">
      <div>
        <h1 className="page-title serif" style={{ fontSize: 34 }}>Sell on Pacific</h1>
        <p className="page-subtitle">Open a store, list your products and reach Pacific shoppers.</p>
      </div>
      <div className="perk-grid">
        {PERKS.map(([icon, text]) => <div key={text} className="square-review-box static perk"><span aria-hidden="true" style={{ fontSize: 26 }}>{icon}</span><span>{text}</span></div>)}
      </div>

      {!user ? (
        <div className="notice">
          <Link to="/register" state={{ next: '/sell' }}>Create an account</Link> or <Link to="/login" state={{ next: location.pathname }}>sign in</Link> to apply. Sellers use their normal customer account.
        </div>
      ) : user.role !== 'CUSTOMER' ? (
        <div className="notice">Admin accounts can't sell. Sign in with a customer account to apply.</div>
      ) : loading ? <div className="loading">Loading…</div>
      : !seller ? <ApplyForm onDone={refresh} />
      : seller.status === 'PENDING' ? (
        <div className="square-review-box static stack"><h2 style={{ margin: 0 }}>Application received</h2><p style={{ margin: 0 }}><b>{seller.storeName}</b> is awaiting review. We'll approve it as soon as we've had a look — check back here.</p></div>
      ) : seller.status === 'APPROVED' ? (
        <div className="square-review-box static stack"><h2 style={{ margin: 0 }}>You're a Pacific seller 🎉</h2><p style={{ margin: 0 }}>Your store <Link to={`/sellers/${seller.slug}`}>{seller.storeName}</Link> is live.</p><div><Link className="submit-btn" to="/seller">Open Seller Central</Link></div></div>
      ) : seller.status === 'SUSPENDED' ? (
        <div className="notice error">Your seller account is suspended{seller.statusNote ? `: ${seller.statusNote}` : '.'} Your listings are hidden until it's reinstated.</div>
      ) : (
        <>
          <div className="notice error">Your application wasn't approved{seller.statusNote ? `: ${seller.statusNote}` : '.'}</div>
          <ApplyForm initial={seller} onDone={refresh} />
        </>
      )}
    </div>
  );
}
