import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api/client';
import type { AdminReview, AdminStats, Page } from '../../api/types';
import { PieChart } from '../../components/PieChart';
import { ReviewCard } from '../../components/ReviewCard';
import { money } from '../../ui/format';
import { useAsync } from '../../ui/useAsync';

export function AdminDashboard() {
  const stats = useAsync(() => api<AdminStats>('/admin/stats'), []);
  const recent = useAsync(() => api<Page<AdminReview>>('/admin/reviews', { query: { size: 6 } }), []);
  const s = stats.data;
  const [showAllRatings, setShowAllRatings] = useState(false);
  const RATINGS_SHOWN = 10; // a real catalogue has hundreds of products; the top of the list is what matters

  return (
    <div className="page">
      {stats.error && <div className="notice error">{stats.error}</div>}
      {!s ? <div className="loading">Loading…</div> : (
        <>
          <section className="stack" style={{ gap: 20 }}>
            <h1 className="page-title">Store overview</h1>
            <div className="kpi-grid">
              <Link to="/admin/orders" className="square-review-box kpi" style={{ textDecoration: 'none' }}><span className="value">{money(s.revenue)}</span><span className="label">Gross sales (excl. cancelled)</span></Link>
              <Link to="/admin/orders" className="square-review-box kpi" style={{ textDecoration: 'none' }}><span className="value">{s.orderCount}</span><span className="label">Orders · {s.ordersByStatus.PLACED} new</span></Link>
              <Link to="/admin/products" className="square-review-box kpi" style={{ textDecoration: 'none' }}><span className="value">{s.activeProducts}</span><span className="label">Active products</span></Link>
              <div className="square-review-box kpi static"><span className="value">{s.customers}</span><span className="label">Customers</span></div>
            </div>
            <div className="kpi-grid">
              <div className="square-review-box kpi static"><span className="value">{money(s.commissionEarned)}</span><span className="label">Commission earned</span></div>
              <Link to="/admin/sellers?status=APPROVED" className="square-review-box kpi" style={{ textDecoration: 'none' }}><span className="value">{s.approvedSellers}</span><span className="label">Active sellers</span></Link>
              <Link to="/admin/sellers?status=PENDING" className="square-review-box kpi" style={{ textDecoration: 'none' }}><span className="value">{s.pendingSellers}</span><span className="label">Applications to review</span></Link>
            </div>
          </section>

          <section className="stack" style={{ gap: 20 }}>
            <h2 className="page-title">Review Distribution Statistics</h2>
            <div className="square-review-box static chart-box">
              <PieChart slices={[
                { label: 'Flagged', value: s.reviews.flagged, color: '#e74c3c' },
                { label: '5 Stars', value: s.reviews.fiveStar, color: '#860752' },
                { label: 'Other', value: s.reviews.other, color: '#f0b8d0' },
              ]} />
              <div className="stack" style={{ gap: 15 }}>
                <div style={{ fontSize: 20, fontWeight: 'bold' }}>Summary</div>
                <div style={{ fontSize: 16, color: '#666' }}>{s.reviews.flagged} flagged out of {s.reviews.total} total reviews</div>
                <hr />
                <Link to="/admin/flagged" className="submit-btn">See more flagged reviews</Link>
              </div>
            </div>
          </section>

          <hr />

          <section className="stack" style={{ gap: 15 }}>
            <h2 className="page-title" style={{ fontSize: 24 }}>Average Ratings per Product</h2>
            <div className="square-review-box static">
              {s.productRatings.length === 0 ? <div className="muted">No product reviews yet.</div> : (showAllRatings ? s.productRatings : s.productRatings.slice(0, RATINGS_SHOWN)).map((p) => (
                <div className="rating-row" key={p.productId}>
                  <span className="name">{p.name}</span>
                  <div className="progress" aria-hidden="true"><span style={{ width: `${(p.average / 5) * 100}%` }} /></div>
                  <span className="avg-label">{p.average > 0 ? `${p.average.toFixed(1)} ★` : 'No ratings'}</span>
                  <span className="count-label">({p.reviewCount} review{p.reviewCount === 1 ? '' : 's'})</span>
                  {p.average >= 4.5 && p.reviewCount > 0 && <span className="badge">⭐ Top Rated</span>}
                </div>
              ))}
              {s.productRatings.length > RATINGS_SHOWN && (
                <button className="ghost-btn" style={{ alignSelf: 'flex-start', marginTop: 8 }} onClick={() => setShowAllRatings((v) => !v)}>
                  {showAllRatings ? `Show top ${RATINGS_SHOWN} only` : `Show all ${s.productRatings.length} products`}
                </button>
              )}
            </div>
          </section>

          {s.lowStock.length > 0 && (
            <section className="stack" style={{ gap: 15 }}>
              <h2 className="page-title" style={{ fontSize: 24 }}>Low stock</h2>
              <div className="square-review-box static">
                {s.lowStock.map((p) => (
                  <div className="rating-row" key={p.productId}>
                    <span className="name">{p.name}</span>
                    <span className={p.stock === 0 ? 'error-text' : 'avg-label'}>{p.stock === 0 ? 'Out of stock' : `${p.stock} left`}</span>
                    <Link to={`/admin/products/${p.productId}`} className="view-all-link">Restock</Link>
                  </div>
                ))}
              </div>
            </section>
          )}
        </>
      )}

      <hr />

      <section className="stack" style={{ gap: 20 }}>
        <div className="row">
          <h2 className="page-title" style={{ fontSize: 24 }}>Recent Flags &amp; Reviews</h2>
          <span className="spacer" />
          <Link to="/admin/flagged" className="view-all-link">See more flagged reviews →</Link>
        </div>
        {recent.data && recent.data.items.length === 0 && <div className="empty">No reviews yet.</div>}
        <div className="review-grid">
          {recent.data?.items.map(({ review }) => (
            <ReviewCard key={review.id} review={review} showProduct
              note={review.status === 'FLAGGED' ? <span className="badge warn" style={{ alignSelf: 'flex-start' }}>Flagged</span> : null}
              actions={review.status === 'FLAGGED' && <Link to={`/admin/reviews/${review.id}/edit`} className="mini-icon-btn" style={{ textDecoration: 'none', color: 'inherit' }} title="Review">✎</Link>} />
          ))}
        </div>
      </section>
    </div>
  );
}
