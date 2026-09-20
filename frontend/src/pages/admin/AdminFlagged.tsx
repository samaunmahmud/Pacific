import { Link, useNavigate } from 'react-router-dom';
import { api } from '../../api/client';
import type { AdminReview, Page } from '../../api/types';
import { ReviewCard } from '../../components/ReviewCard';
import { useToast } from '../../ui/Toast';
import { useAsync } from '../../ui/useAsync';

/** The old Moderation Queue. */
export function AdminFlagged() {
  const { data, error, loading, reload } = useAsync(() => api<Page<AdminReview>>('/admin/reviews', { query: { status: 'FLAGGED', size: 50 } }), []);
  const navigate = useNavigate();
  const toast = useToast();

  async function act(fn: () => Promise<unknown>, done: string) {
    try {
      await fn();
      toast.show(done);
      reload();
    } catch (e) {
      toast.show(e instanceof Error ? e.message : 'Action failed.', 'error');
    }
  }

  return (
    <div className="page">
      <div className="row" style={{ gap: 20 }}>
        <Link to="/admin" className="back-btn" aria-label="Back to dashboard">←</Link>
        <div>
          <h1 className="page-title">Moderation Queue</h1>
          <p className="error-text" style={{ margin: '4px 0 0' }}>
            {data && data.totalItems === 0 ? 'All clear: nothing is waiting for moderation.' : 'Action required: Reviews flagged for moderation'}
          </p>
        </div>
      </div>
      {error && <div className="notice error">{error}</div>}
      {loading && !data ? <div className="loading">Loading…</div> : (
        <div className="review-grid">
          {data?.items.map(({ review, flagReason }) => (
            <ReviewCard key={review.id} review={review} showProduct
              actions={
                <>
                  <button className="mini-icon-btn" title="Keep review (reject flag)" aria-label="Keep review" onClick={() => act(() => api(`/admin/reviews/${review.id}/dismiss`, { method: 'POST' }), 'Flag rejected — review is visible again')}>✓</button>
                  <button className="mini-icon-btn" title="Edit review" aria-label="Edit review" onClick={() => navigate(`/admin/reviews/${review.id}/edit`)}>✎</button>
                  <button className="mini-icon-btn delete-btn" title="Delete review" aria-label="Delete review" onClick={() => window.confirm('Delete this review permanently?') && act(() => api(`/admin/reviews/${review.id}`, { method: 'DELETE' }), 'Review deleted')}>🗑</button>
                </>
              }
              note={<><span style={{ fontWeight: 'bold' }}>Moderation Required</span>{flagReason && <span className="edit-note">Reason given: {flagReason}</span>}</>} />
          ))}
        </div>
      )}
    </div>
  );
}
