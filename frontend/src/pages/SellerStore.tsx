import { useState, type FormEvent } from 'react';
import { Link, useLocation, useParams, useSearchParams } from 'react-router-dom';
import { api } from '../api/client';
import type { Page, Product, SellerRating, SellerStorePage } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { Pagination } from '../components/Pagination';
import { ProductCard } from '../components/ProductCard';
import { Stars } from '../components/Stars';
import { dateOnly } from '../ui/format';
import { useToast } from '../ui/Toast';
import { useAsync } from '../ui/useAsync';

function RateSeller({ slug, own, onDone }: { slug: string; own: SellerRating | undefined; onDone: () => void }) {
  const toast = useToast();
  const [rating, setRating] = useState(own?.rating ?? 0);
  const [comment, setComment] = useState(own?.comment ?? '');
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (rating === 0) return toast.show('Please choose a star rating.', 'error');
    setBusy(true);
    try {
      await api(`/sellers/${slug}/rating`, { method: 'PUT', body: { rating, comment: comment.trim() || null } });
      toast.show(own ? 'Rating updated' : 'Thanks for rating this seller!');
      onDone();
    } catch (err) {
      toast.show(err instanceof Error ? err.message : 'Could not save your rating.', 'error');
    } finally {
      setBusy(false);
    }
  }

  async function remove() {
    if (!window.confirm('Remove your rating?')) return;
    try {
      await api(`/sellers/${slug}/rating`, { method: 'DELETE' });
      toast.show('Rating removed');
      onDone();
    } catch (err) {
      toast.show(err instanceof Error ? err.message : 'Could not remove your rating.', 'error');
    }
  }

  return (
    <form className="square-review-box static stack" onSubmit={submit}>
      <b>{own ? 'Your rating of this seller' : 'Rate this seller'}</b>
      <div className="row" style={{ gap: 5 }} role="radiogroup" aria-label="Seller rating">
        {[1, 2, 3, 4, 5].map((n) => (
          <button type="button" key={n} className={`star-button ${n <= rating ? 'on' : ''}`} onClick={() => setRating(n)} role="radio" aria-checked={n === rating} aria-label={`${n} star${n === 1 ? '' : 's'}`}>{n <= rating ? '★' : '☆'}</button>
        ))}
      </div>
      <input className="rounded-input" placeholder="How was delivery and service? (optional)" value={comment} onChange={(e) => setComment(e.target.value)} maxLength={500} aria-label="Comment" />
      <div className="row">
        <button className="submit-btn" disabled={busy}>{busy ? 'Saving…' : own ? 'Update rating' : 'Submit rating'}</button>
        {own && <button type="button" className="ghost-btn" onClick={remove}>Remove</button>}
      </div>
    </form>
  );
}

/** Public seller storefront: "Sold by …" links land here. */
export function SellerStore() {
  const { slug = '' } = useParams();
  const { user } = useAuth();
  const location = useLocation();
  const [params, setParams] = useSearchParams();
  const page = Number(params.get('page') ?? '0') || 0;

  const store = useAsync(() => api<SellerStorePage>(`/sellers/${slug}`), [slug, user?.id]);
  const products = useAsync(() => api<Page<Product>>('/products', { query: { seller: slug, page, size: 12 } }), [slug, page]);

  if (store.error && !store.data) return <div className="page page-narrow"><div className="notice error">{store.error}</div><Link to="/products" className="submit-btn" style={{ alignSelf: 'flex-start' }}>Back to shopping</Link></div>;
  if (!store.data) return <div className="loading">Loading…</div>;
  const { seller, summary, reviews, eligibility } = store.data;
  const own = reviews.find((r) => r.mine);

  return (
    <div className="page">
      <section className="square-review-box static store-header">
        <div className="store-avatar" aria-hidden="true">{seller.storeName.charAt(0).toUpperCase()}</div>
        <div className="stack" style={{ gap: 6 }}>
          <h1 className="page-title" style={{ fontSize: 32 }}>{seller.storeName}</h1>
          <Stars value={seller.ratingAvg} count={seller.ratingCount} />
          <div className="muted">Selling on Pacific since {dateOnly(seller.since)} · {seller.productCount} product{seller.productCount === 1 ? '' : 's'}</div>
          {seller.description && <p style={{ margin: '6px 0 0', color: 'var(--text-2)', maxWidth: 720 }}>{seller.description}</p>}
        </div>
      </section>

      <section className="stack" style={{ gap: 20 }}>
        <h2 className="section-title">Products from {seller.storeName}</h2>
        {products.error && <div className="notice error">{products.error}</div>}
        {products.data && products.data.items.length === 0 ? <div className="empty">No products listed right now.</div> : (
          <div className="product-grid">{products.data?.items.map((p) => <ProductCard key={p.id} product={p} />)}</div>
        )}
        {products.data && <Pagination page={products.data.page} totalPages={products.data.totalPages} onChange={(p) => setParams({ page: String(p) })} />}
      </section>

      <hr />

      <section className="stack" style={{ gap: 20 }} aria-labelledby="sr-h">
        <h2 className="section-title" id="sr-h">Seller ratings</h2>
        <div className="square-review-box static summary-grid">
          <div className="stack" style={{ gap: 8 }}>
            <div className="big-rating">{summary.count ? summary.average.toFixed(1) : '–'}</div>
            <Stars value={summary.average} />
            <div className="muted">{summary.count} rating{summary.count === 1 ? '' : 's'}</div>
          </div>
          <div className="stack" style={{ gap: 6 }}>
            {[5, 4, 3, 2, 1].map((star) => (
              <div key={star} className="dist-row">
                <span style={{ width: 28 }}>{star} ★</span>
                <div className="progress"><span style={{ width: `${summary.count ? (summary.distribution[star - 1] / summary.count) * 100 : 0}%` }} /></div>
                <span style={{ width: 24, textAlign: 'right' }}>{summary.distribution[star - 1]}</span>
              </div>
            ))}
          </div>
        </div>

        {eligibility.ownStore ? <div className="notice">This is your store. <Link to="/seller">Open Seller Central</Link></div>
          : !eligibility.signedIn ? <div className="notice"><Link to="/login" state={{ next: location.pathname }}>Sign in</Link> to rate this seller.</div>
          : eligibility.canRate ? <RateSeller key={own?.id ?? 'new'} slug={slug} own={own} onDone={store.reload} />
          : <div className="notice">You can rate this seller once an order from them has been delivered.</div>}

        {reviews.length === 0 ? <div className="empty" style={{ padding: 20 }}>No ratings yet.</div> : (
          <div className="review-list">
            {reviews.map((r) => (
              <article key={r.id} className="square-review-box review-card">
                <div className="star-label">{'★'.repeat(r.rating)}{'☆'.repeat(5 - r.rating)}</div>
                {r.comment && <p className="review-comment">{r.comment}</p>}
                <div className="reviewer-name">{r.authorName}{r.mine && <span className="badge" style={{ marginLeft: 8 }}>You</span>}</div>
                <div className="review-date">{dateOnly(r.createdAt)}</div>
              </article>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
