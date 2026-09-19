import { useState, type FormEvent } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { api } from '../api/client';
import type { Answer, Questions } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { dateOnly } from '../ui/format';
import { useToast } from '../ui/Toast';
import { useAsync } from '../ui/useAsync';

const LABELS: Record<Answer['label'], string> = { PACIFIC: 'Pacific', SELLER: 'Seller', BUYER: 'Verified buyer', CUSTOMER: 'Customer' };

/** Customer questions and answers for a product. Sellers, Pacific and buyers can answer. */
export function QandA({ productId }: { productId: number }) {
  const { user } = useAuth();
  const toast = useToast();
  const location = useLocation();
  const { data, error, reload } = useAsync(() => api<Questions>(`/products/${productId}/questions`), [productId, user?.id]);
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const [answering, setAnswering] = useState<number | null>(null);
  const [answerText, setAnswerText] = useState('');

  async function run(fn: () => Promise<unknown>, done: string) {
    setBusy(true);
    try {
      await fn();
      toast.show(done);
      reload();
      return true;
    } catch (e) {
      toast.show(e instanceof Error ? e.message : 'Something went wrong.', 'error');
      return false;
    } finally {
      setBusy(false);
    }
  }

  async function ask(e: FormEvent) {
    e.preventDefault();
    if (await run(() => api(`/products/${productId}/questions`, { method: 'POST', body: { text: text.trim() } }), 'Question posted')) setText('');
  }

  async function answer(e: FormEvent, questionId: number) {
    e.preventDefault();
    if (await run(() => api(`/questions/${questionId}/answers`, { method: 'POST', body: { text: answerText.trim() } }), 'Answer posted')) {
      setAnswering(null);
      setAnswerText('');
    }
  }

  return (
    <section className="stack" style={{ gap: 20 }} aria-labelledby="qa-h">
      <h2 className="section-title" id="qa-h">Questions &amp; answers</h2>
      {error && <div className="notice error">{error}</div>}

      {data?.canAsk ? (
        <form className="row" onSubmit={ask}>
          <input className="rounded-input" placeholder="Have a question? Ask the seller and other buyers…" value={text} onChange={(e) => setText(e.target.value)} maxLength={300} aria-label="Your question" />
          <button className="submit-btn" disabled={busy || !text.trim()}>Ask</button>
        </form>
      ) : !user ? (
        <div className="notice"><Link to="/login" state={{ next: location.pathname }}>Sign in</Link> to ask a question.</div>
      ) : null}

      {data && data.questions.length === 0 && <div className="empty" style={{ padding: 20 }}>No questions yet. Be the first to ask.</div>}
      <div className="stack">
        {data?.questions.map((q) => (
          <div key={q.id} className="square-review-box static qa-item">
            <div className="row">
              <div><b>Q:</b> {q.text}</div>
              <span className="spacer" />
              {q.canDelete && <button className="report-link" onClick={() => window.confirm('Delete this question and its answers?') && run(() => api(`/questions/${q.id}`, { method: 'DELETE' }), 'Question deleted')}>Delete</button>}
            </div>
            <div className="muted" style={{ fontSize: 12 }}>Asked by {q.askerName} · {dateOnly(q.createdAt)}</div>
            {q.answers.map((a) => (
              <div key={a.id} className="qa-answer">
                <div className="row">
                  <div><b>A:</b> {a.text}</div>
                  <span className="spacer" />
                  {a.canDelete && <button className="report-link" onClick={() => window.confirm('Delete this answer?') && run(() => api(`/answers/${a.id}`, { method: 'DELETE' }), 'Answer deleted')}>Delete</button>}
                </div>
                <div className="row" style={{ gap: 8, fontSize: 12 }}>
                  <span className={`badge ${a.label === 'SELLER' || a.label === 'PACIFIC' ? '' : 'ok'}`}>{LABELS[a.label]}</span>
                  <span className="muted">{a.authorName} · {dateOnly(a.createdAt)}</span>
                </div>
              </div>
            ))}
            {data.canAnswer && (answering === q.id ? (
              <form className="row" onSubmit={(e) => answer(e, q.id)}>
                <input className="rounded-input" autoFocus placeholder="Write your answer…" value={answerText} onChange={(e) => setAnswerText(e.target.value)} maxLength={500} aria-label="Your answer" />
                <button className="submit-btn" disabled={busy || !answerText.trim()}>Post</button>
                <button type="button" className="ghost-btn" onClick={() => setAnswering(null)}>Cancel</button>
              </form>
            ) : (
              <div><button className="view-all-link" onClick={() => { setAnswering(q.id); setAnswerText(''); }}>Answer this question</button></div>
            ))}
          </div>
        ))}
      </div>
    </section>
  );
}
