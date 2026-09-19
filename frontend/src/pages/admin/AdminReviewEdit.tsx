import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../../api/client';
import type { AdminReview } from '../../api/types';
import { stars } from '../../ui/format';
import { useToast } from '../../ui/Toast';

/** The old "Edit Flagged Review" screen. Saving clears the flag; "Reject Flag" keeps the text as written. */
export function AdminReviewEdit() {
  const { id } = useParams();
  const navigate = useNavigate();
  const toast = useToast();
  const [review, setReview] = useState<AdminReview | null>(null);
  const [title, setTitle] = useState('');
  const [comment, setComment] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api<AdminReview>(`/admin/reviews/${id}`)
      .then((r) => {
        setReview(r);
        setTitle(r.review.title ?? '');
        setComment(r.review.comment);
      })
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Could not load the review.'));
  }, [id]);

  async function save(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      await api(`/admin/reviews/${id}`, { method: 'PUT', body: { title: title.trim() || null, comment: comment.trim() } });
      toast.show('Review updated and cleared');
      navigate('/admin/flagged');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not save.');
    } finally {
      setBusy(false);
    }
  }

  async function reject() {
    setBusy(true);
    try {
      await api(`/admin/reviews/${id}/dismiss`, { method: 'POST' });
      toast.show('Flag rejected — review is visible again');
      navigate('/admin/flagged');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not reject the flag.');
      setBusy(false);
    }
  }

  if (!review && error) return <div className="page page-narrow"><div className="notice error">{error}</div></div>;
  if (!review) return <div className="loading">Loading…</div>;

  return (
    <form className="page page-narrow" onSubmit={save}>
      <div className="row" style={{ gap: 20 }}>
        <Link to="/admin/flagged" className="three-dots-btn" style={{ textDecoration: 'none', fontSize: 24 }} aria-label="Back">←</Link>
        <h1 className="page-title" style={{ color: 'var(--brand-dark)' }}>Edit Flagged Review</h1>
      </div>
      <div className="square-review-box static stack">
        <div className="muted">{review.review.productName} · <span className="star-label" style={{ fontSize: 15 }}>{stars(review.review.rating)}</span> · by {review.review.authorName}</div>
        {review.flagReason && <div className="notice error">Reported: {review.flagReason}</div>}
        <label className="field-label small" htmlFor="t">Review Title</label>
        <input id="t" className="rounded-input" value={title} onChange={(e) => setTitle(e.target.value)} maxLength={120} />
        <label className="field-label small" htmlFor="c">Review Content</label>
        <textarea id="c" className="rounded-input" value={comment} onChange={(e) => setComment(e.target.value)} maxLength={500} required style={{ minHeight: 150 }} />
        {error && <div className="notice error" role="alert">{error}</div>}
        <div className="row" style={{ justifyContent: 'flex-end', gap: 20 }}>
          <button type="button" className="ghost-btn" onClick={reject} disabled={busy}>Reject Flag</button>
          <button className="submit-btn" disabled={busy}>{busy ? 'Saving…' : 'Save Changes'}</button>
        </div>
      </div>
    </form>
  );
}
