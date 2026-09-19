import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { api } from '../api/client';
import type { Product, ProductReviews, Review } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { useCart } from '../cart/CartContext';
import { Price } from '../components/Price';
import { ProductImage } from '../components/ProductImage';
import { QandA } from '../components/QandA';
import { RecentlyViewed } from '../components/RecentlyViewed';
import { CustomerReviewCard } from '../components/ReviewCard';
import { Stars } from '../components/Stars';
import { WishlistButton } from '../components/WishlistButton';
import { recordView } from '../ui/recent';
import { useToast } from '../ui/Toast';
import { useAsync } from '../ui/useAsync';

const SORTS: [string, string][] = [
  ['newest', 'Newest'],
  ['helpful', 'Most Helpful'],
  ['highest', 'Highest Rating'],
  ['lowest', 'Lowest Rating'],
];

export function ProductDetail() {
  const { id } = useParams();
  const { user } = useAuth();
  const { add, cart } = useCart();
  const toast = useToast();
  const navigate = useNavigate();
  const location = useLocation();
  const [qty, setQty] = useState(1);
  const [busy, setBusy] = useState(false);
  const [sort, setSort] = useState('newest');

  const product = useAsync(() => api<Product>(`/products/${id}`), [id]);
  const reviews = useAsync(() => api<ProductReviews>(`/products/${id}/reviews`, { query: { sort } }), [id, sort, user?.id]);
  const loadedId = product.data?.id;
  useEffect(() => {
    if (loadedId !== undefined) recordView(loadedId);
  }, [loadedId]);

  if (product.error && !product.data) {
    return (
      <div className="page page-narrow">
        <div className="notice error">{product.error}</div>
        <Link className="submit-btn" style={{ alignSelf: 'flex-start' }} to="/products">Back to all products</Link>
      </div>
    );
  }
  const p = product.data;
  if (!p) return <div className="loading">Loading…</div>;

  const inCart = cart?.items.find((i) => i.productId === p.id)?.quantity ?? 0;
  const canAddMore = Math.max(0, Math.min(p.stock, 10) - inCart);

  async function addToCart() {
    if (!p) return;
    if (!user) return navigate('/login', { state: { next: location.pathname } });
    if (user.role !== 'CUSTOMER') return toast.show('Sign in with a customer account to shop.', 'error');
    setBusy(true);
    try {
      await add(p.id, qty);
      toast.show(`Added ${qty} × ${p.name} to your cart`);
      setQty(1);
    } catch (e) {
      toast.show(e instanceof Error ? e.message : 'Could not add to cart.', 'error');
    } finally {
      setBusy(false);
    }
  }

  const data = reviews.data;
  const replace = (r: Review) => reviews.setData((prev) => prev && { ...prev, reviews: prev.reviews.map((x) => (x.id === r.id ? r : x)) });
  const drop = () => reviews.reload(); // counts/eligibility change too, so refetch

  return (
    <div className="page">
      <div><Link to="/products" className="back-btn" aria-label="Back to products">←</Link></div>

      <div className="pdp">
        <div className="image-container">
          <ProductImage imageUrl={p.imageUrl} categoryName={p.category?.name} alt={p.name} />
        </div>
        <div className="stack">
          {p.category && <Link to={`/products?category=${p.category.slug}`} className="badge" style={{ alignSelf: 'flex-start', textDecoration: 'none' }}>{p.category.name}</Link>}
          <h1 className="page-title">{p.name}</h1>
          <Stars value={p.ratingAvg} count={p.ratingCount} />
          <div className="muted">
            Sold by {p.sellerSlug ? <Link to={`/sellers/${p.sellerSlug}`} style={{ fontWeight: 'bold' }}>{p.sellerName}</Link> : <b>{p.sellerName}</b>}
          </div>
          <Price price={p.price} listPrice={p.listPrice} discountPercent={p.discountPercent} large />
          {p.description && <p style={{ margin: 0, color: '#555' }}>{p.description}</p>}
          <div>
            {p.stock === 0 ? <span className="badge warn">Out of stock</span>
              : p.stock <= 5 ? <span className="badge warn">Only {p.stock} left in stock</span>
              : <span className="badge ok">In stock</span>}
          </div>
          {p.stock > 0 && (
            <div className="row-wrap">
              <div className="qty" aria-label="Quantity">
                <button onClick={() => setQty((q) => Math.max(1, q - 1))} disabled={qty <= 1} aria-label="Decrease">−</button>
                <span>{qty}</span>
                <button onClick={() => setQty((q) => Math.min(canAddMore || 1, q + 1))} disabled={qty >= canAddMore} aria-label="Increase">+</button>
              </div>
              <button className="submit-btn" onClick={addToCart} disabled={busy || canAddMore === 0}>
                {canAddMore === 0 ? 'Max quantity in cart' : busy ? 'Adding…' : 'Add to cart'}
              </button>
              {inCart > 0 && <Link to="/cart" className="view-all-link">{inCart} in your cart</Link>}
            </div>
          )}
          <div><WishlistButton productId={p.id} label /></div>
        </div>
      </div>

      <hr />

      <section className="stack" style={{ gap: 20 }} aria-labelledby="reviews-h">
        <div className="row-wrap">
          <h2 className="section-title" id="reviews-h">Customer reviews</h2>
          <span className="spacer" />
          <select className="pill-select" value={sort} onChange={(e) => setSort(e.target.value)} aria-label="Sort reviews">
            {SORTS.map(([v, label]) => <option key={v} value={v}>{label}</option>)}
          </select>
        </div>

        {reviews.error && <div className="notice error">{reviews.error}</div>}
        {data && (
          <>
            <div className="square-review-box static summary-grid">
              <div className="stack" style={{ gap: 8 }}>
                <div className="big-rating">{data.summary.count ? data.summary.average.toFixed(1) : '–'}</div>
                <Stars value={data.summary.average} />
                <div className="muted">{data.summary.count} review{data.summary.count === 1 ? '' : 's'}</div>
              </div>
              <div className="stack" style={{ gap: 6 }}>
                {[5, 4, 3, 2, 1].map((star) => {
                  const n = data.summary.distribution[star - 1];
                  return (
                    <div key={star} className="dist-row">
                      <span style={{ width: 28 }}>{star} ★</span>
                      <div className="progress"><span style={{ width: `${data.summary.count ? (n / data.summary.count) * 100 : 0}%` }} /></div>
                      <span style={{ width: 24, textAlign: 'right' }}>{n}</span>
                    </div>
                  );
                })}
              </div>
            </div>

            <div>
              {!data.eligibility.signedIn ? (
                <Link className="submit-btn" to="/login" state={{ next: location.pathname }}>Sign in to write a review</Link>
              ) : data.eligibility.hasReviewed ? (
                <div className="notice">You've reviewed this product — thanks! You can find it under <Link to="/account/reviews">My Reviews</Link>.</div>
              ) : data.eligibility.purchased ? (
                <Link className="submit-btn" to={`/products/${p.id}/review`}>Write a review</Link>
              ) : (
                <div className="notice">You can review this product once you've bought it.</div>
              )}
            </div>

            {data.reviews.length === 0 ? (
              <div className="empty">No reviews yet.</div>
            ) : (
              <div className="review-grid">
                {data.reviews.map((r) => <CustomerReviewCard key={r.id} review={r} onChange={replace} onDelete={drop} />)}
              </div>
            )}
          </>
        )}
      </section>

      <hr />
      <QandA productId={p.id} />
      <RecentlyViewed exclude={p.id} />
    </div>
  );
}
