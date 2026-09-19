import { useState, type ReactNode } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import avatar from '../assets/avatarCustomerLogin.png';
import { api } from '../api/client';
import type { Review, VoteType } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { dateTime, minutesLeft, stars } from '../ui/format';
import { useToast } from '../ui/Toast';

/** Presentational review card (the old `square-review-box`). Actions go in the top-right, `footer` under the divider. */
export function ReviewCard({
  review,
  actions,
  footer,
  showProduct,
  note,
}: {
  review: Review;
  actions?: ReactNode;
  footer?: ReactNode;
  showProduct?: boolean;
  note?: ReactNode;
}) {
  return (
    <article className="square-review-box review-card">
      {actions && <div className="review-card-top">{actions}</div>}
      {showProduct && (
        <Link className="review-product" to={`/products/${review.productId}`}>
          {review.productName}
        </Link>
      )}
      <div className="star-label" aria-label={`${review.rating} out of 5 stars`}>
        {stars(review.rating)}
      </div>
      {review.title && <div className="review-title">{review.title}</div>}
      <p className="review-comment">{review.comment}</p>
      {review.status === 'FLAGGED' && review.mine && (
        <span className="badge warn" style={{ alignSelf: 'flex-start' }}>Under review by our moderators</span>
      )}
      {review.editedByAdmin && <span className="edit-note">Edited by a moderator</span>}
      {note}
      <div className="reviewer">
        <div className="avatar-circle"><img src={avatar} alt="" /></div>
        <div>
          <div className="reviewer-name">{review.authorName}</div>
          <div className="review-date">{dateTime(review.createdAt)}</div>
        </div>
      </div>
      {footer && (
        <>
          <hr />
          {footer}
        </>
      )}
    </article>
  );
}

/** The customer-facing card: votes, report, and edit/delete while the edit window is open. */
export function CustomerReviewCard({
  review,
  showProduct,
  onChange,
  onDelete,
}: {
  review: Review;
  showProduct?: boolean;
  onChange: (r: Review) => void;
  onDelete: (id: number) => void;
}) {
  const { user } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  const isCustomer = user?.role === 'CUSTOMER';

  async function vote(type: VoteType) {
    if (!isCustomer) return toast.show('Sign in with a customer account to vote.', 'error');
    setBusy(true);
    try {
      onChange(await api<Review>(`/reviews/${review.id}/vote`, { method: 'POST', body: { type } }));
    } catch (e) {
      toast.show(e instanceof Error ? e.message : 'Could not save your vote.', 'error');
    } finally {
      setBusy(false);
    }
  }

  async function remove() {
    if (!window.confirm('Are you sure you want to delete this review?')) return;
    try {
      await api(`/reviews/${review.id}`, { method: 'DELETE' });
      onDelete(review.id);
      toast.show('Review deleted');
    } catch (e) {
      toast.show(e instanceof Error ? e.message : 'Could not delete the review.', 'error');
    }
  }

  async function report() {
    if (!isCustomer) return toast.show('Sign in with a customer account to report.', 'error');
    if (!window.confirm('Report this review to our moderators? It will be hidden until they have looked at it.')) return;
    try {
      await api(`/reviews/${review.id}/report`, { method: 'POST', body: {} });
      onDelete(review.id); // it disappears from the public list for the reporter too
      toast.show('Thanks — our moderators will take a look.');
    } catch (e) {
      toast.show(e instanceof Error ? e.message : 'Could not report the review.', 'error');
    }
  }

  const left = minutesLeft(review.editableUntil);
  return (
    <ReviewCard
      review={review}
      showProduct={showProduct}
      actions={
        review.canEdit && (
          <>
            <button className="mini-icon-btn" title="Edit review" aria-label="Edit review" onClick={() => navigate(`/reviews/${review.id}/edit`)}>✎</button>
            <button className="mini-icon-btn delete-btn" title="Delete review" aria-label="Delete review" onClick={remove}>🗑</button>
          </>
        )
      }
      note={review.canEdit && <span className="edit-note">You can edit or delete this for {left} more minute{left === 1 ? '' : 's'}.</span>}
      footer={
        review.mine ? (
          <div className="vote-row">
            <span className="hint">Your review</span>
            <span className="spacer" />
            <span aria-hidden="true">👍</span><span className="vote-count-label">{review.helpfulCount}</span>
            <span aria-hidden="true">👎</span><span className="vote-count-label">{review.unhelpfulCount}</span>
          </div>
        ) : (
          <div className="vote-row">
            <span className="hint">Was this helpful?</span>
            <span className="spacer" />
            <button className={`helpful-btn ${review.myVote === 'HELPFUL' ? 'mine' : ''}`} disabled={busy} onClick={() => vote('HELPFUL')} aria-label="Helpful" aria-pressed={review.myVote === 'HELPFUL'}>👍</button>
            <span className="vote-count-label">{review.helpfulCount}</span>
            <button className={`helpful-btn ${review.myVote === 'UNHELPFUL' ? 'mine' : ''}`} disabled={busy} onClick={() => vote('UNHELPFUL')} aria-label="Not helpful" aria-pressed={review.myVote === 'UNHELPFUL'}>👎</button>
            <span className="vote-count-label">{review.unhelpfulCount}</span>
            <button className="report-link" onClick={report}>Report</button>
          </div>
        )
      }
    />
  );
}
