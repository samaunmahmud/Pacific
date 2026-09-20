import { Link, useNavigate } from 'react-router-dom';
import { useState } from 'react';
import { api } from '../api/client';
import type { Product, Review } from '../api/types';
import { CustomerReviewCard } from '../components/ReviewCard';
import { ProductImage } from '../components/ProductImage';
import { money } from '../ui/format';
import { useAsync } from '../ui/useAsync';

const SORTS: [string, string][] = [
  ['newest', 'Newest'],
  ['highest', 'Highest Rating'],
  ['lowest', 'Lowest Rating'],
  ['helpful', 'Most Helpful'],
];

function UnratedCard({ product }: { product: Product }) {
  const navigate = useNavigate();
  return (
    <div className="unrated-card" onClick={() => navigate(`/products/${product.id}/review`)} role="link" tabIndex={0} onKeyDown={(e) => e.key === 'Enter' && navigate(`/products/${product.id}/review`)}>
      <div className="image-container"><ProductImage imageUrl={product.imageUrl} categoryName={product.category?.name} alt={product.name} /></div>
      <h3 className="unrated-title">{product.name}</h3>
      <div className="unrated-price">{money(product.price)}</div>
      <button className="submit-btn block">Write Review</button>
    </div>
  );
}

/** The old CustomerDashboard: unrated purchases on top, then the customer's own reviews with search + sort. */
export function MyReviews() {
  const [q, setQ] = useState('');
  const [sort, setSort] = useState('newest');
  const unrated = useAsync(() => api<Product[]>('/me/unrated-products'), []);
  const mine = useAsync(() => api<Review[]>('/me/reviews', { query: { q, sort } }), [q, sort]);

  return (
    <div className="page">
      <section className="stack" style={{ gap: 20 }}>
        <div className="row">
          <div>
            <h1 className="page-title">Waiting for your review</h1>
            <p className="page-subtitle">Items you recently purchased</p>
          </div>
          <span className="spacer" />
          <Link to="/account/unrated" className="view-all-link">More</Link>
        </div>
        {unrated.error && <div className="notice error">{unrated.error}</div>}
        {unrated.data && unrated.data.length === 0 ? (
          <div className="empty">Nothing waiting for a review. <Link to="/products">Keep shopping</Link></div>
        ) : (
          <div className="product-grid">{unrated.data?.slice(0, 4).map((p) => <UnratedCard key={p.id} product={p} />)}</div>
        )}
      </section>

      <div className="stack" style={{ gap: 20 }}>
        <div className="filter-row">
          <div className="customer-search-box">
            <span className="customer-search-icon" aria-hidden="true">🔍</span>
            <input className="customer-search-field" placeholder="Search your reviews..." value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search your reviews" maxLength={100} />
          </div>
          <select className="pill-select" value={sort} onChange={(e) => setSort(e.target.value)} aria-label="Sort by">
            {SORTS.map(([v, label]) => <option key={v} value={v}>{label}</option>)}
          </select>
        </div>
        <hr />
      </div>

      <section className="stack" style={{ gap: 20 }}>
        <h2 className="section-title">Recently Rated Products</h2>
        {mine.error && <div className="notice error">{mine.error}</div>}
        {mine.data && mine.data.length === 0 ? (
          <div className="empty">{q ? 'No reviews match your search.' : "You haven't reviewed anything yet."}</div>
        ) : (
          <div className="review-list">
            {mine.data?.map((r) => (
              <CustomerReviewCard key={r.id} review={r} showProduct
                onChange={(u) => mine.setData((prev) => prev?.map((x) => (x.id === u.id ? u : x)))}
                onDelete={() => { mine.reload(); unrated.reload(); }} />
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

export function UnratedProducts() {
  const { data, error, loading } = useAsync(() => api<Product[]>('/me/unrated-products'), []);
  return (
    <div className="page">
      <div className="row" style={{ gap: 20 }}>
        <Link to="/account/reviews" className="back-btn" aria-label="Back">←</Link>
        <div>
          <h1 className="page-title" style={{ fontSize: 32 }}>Unrated Products</h1>
          <p className="page-subtitle">Items waiting for your feedback</p>
        </div>
      </div>
      {error && <div className="notice error">{error}</div>}
      {loading && !data ? <div className="loading">Loading…</div> : data && data.length === 0 ? (
        <div className="empty">You're all caught up. <Link to="/products">Keep shopping</Link></div>
      ) : (
        <div className="product-grid">{data?.map((p) => <UnratedCard key={p.id} product={p} />)}</div>
      )}
    </div>
  );
}
