import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import avatar from '../assets/avatarCustomerLogin.png';
import { api } from '../api/client';
import type { Product, Review } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { useToast } from '../ui/Toast';

/** The old ReviewPage. Route /products/:productId/review creates; /reviews/:reviewId/edit edits. */
export function ReviewForm() {
  const { productId, reviewId } = useParams();
  const editing = reviewId !== undefined;
  const { user } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();

  const [productName, setProductName] = useState('');
  const [rating, setRating] = useState(0);
  const [title, setTitle] = useState('');
  const [comment, setComment] = useState('');
  const [imageUrl, setImageUrl] = useState('');
  const [error, setError] = useState('');
  const [loadError, setLoadError] = useState('');
  const [loaded, setLoaded] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        if (editing) {
          const mine = await api<Review[]>('/me/reviews');
          const r = mine.find((x) => String(x.id) === reviewId);
          if (!r) throw new Error('Review not found.');
          if (!r.canEdit) throw new Error('The edit window for this review has closed.');
          if (cancelled) return;
          setProductName(r.productName);
          setRating(r.rating);
          setTitle(r.title ?? '');
          setComment(r.comment);
          setImageUrl(r.imageUrl ?? '');
        } else {
          const p = await api<Product>(`/products/${productId}`);
          if (cancelled) return;
          setProductName(p.name);
        }
        setLoaded(true);
      } catch (e) {
        if (!cancelled) setLoadError(e instanceof Error ? e.message : 'Could not load the form.');
      }
    })();
    return () => { cancelled = true; };
  }, [editing, reviewId, productId]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError('');
    if (rating === 0) return setError('Please choose a star rating.');
    if (!comment.trim()) return setError('Please write a comment.');
    setBusy(true);
    try {
      const body = { rating, title: title.trim() || null, comment: comment.trim(), imageUrl: imageUrl.trim() || null };
      if (editing) await api(`/reviews/${reviewId}`, { method: 'PUT', body });
      else await api(`/products/${productId}/reviews`, { method: 'POST', body });
      toast.show(editing ? 'Review updated' : 'Thanks for your review!');
      navigate('/account/reviews', { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not save your review.');
    } finally {
      setBusy(false);
    }
  }

  if (loadError) return <div className="page page-narrow"><div className="notice error">{loadError}</div><Link to="/account/reviews" className="submit-btn" style={{ alignSelf: 'flex-start' }}>Back to my reviews</Link></div>;
  if (!loaded) return <div className="loading">Loading…</div>;

  return (
    <form className="page page-narrow" style={{ gap: 15 }} onSubmit={submit}>
      <div className="row">
        <Link to="/account/reviews" className="three-dots-btn" style={{ textDecoration: 'none', fontSize: 24 }} aria-label="Back">←</Link>
        <span className="spacer" />
        <span style={{ fontSize: 16 }}>Hello, {user?.name}</span>
        <img src={avatar} alt="" height={40} />
      </div>

      <h1 className="page-title" style={{ fontSize: 32 }}>{editing ? 'Edit your review' : 'How was the item?'}</h1>
      <div className="muted">{productName}</div>

      <div className="row" style={{ gap: 5 }} role="radiogroup" aria-label="Star rating">
        {[1, 2, 3, 4, 5].map((n) => (
          <button type="button" key={n} className={`star-button ${n <= rating ? 'on' : ''}`} onClick={() => setRating(n)} role="radio" aria-checked={n === rating} aria-label={`${n} star${n === 1 ? '' : 's'}`}>
            {n <= rating ? '★' : '☆'}
          </button>
        ))}
      </div>

      <label className="field-label" htmlFor="comment">Write a review,</label>
      <textarea id="comment" className="rounded-input" value={comment} onChange={(e) => setComment(e.target.value)} maxLength={500} required />
      <div className="char-count">{comment.length}/500</div>

      <label className="field-label" htmlFor="title">Title your review</label>
      <input id="title" className="rounded-input" value={title} onChange={(e) => setTitle(e.target.value)} placeholder="What's most important to know?" maxLength={120} />

      <label className="field-label" htmlFor="img">Share a photo (optional)</label>
      <input id="img" className="rounded-input" type="url" value={imageUrl} onChange={(e) => setImageUrl(e.target.value)} placeholder="https://… link to your photo" maxLength={500} />

      {error && <div className="notice error" role="alert">{error}</div>}
      <div style={{ textAlign: 'center' }}>
        <button className="submit-btn" style={{ width: 250 }} disabled={busy}>{busy ? 'Saving…' : editing ? 'Save changes' : 'Submit'}</button>
      </div>
    </form>
  );
}
