import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import type { Conversation, ConversationSummary, MessageSide, Product } from '../api/types';
import { useUnread } from '../messages/UnreadContext';
import { dateTime } from '../ui/format';
import { useAsync } from '../ui/useAsync';

const MAX = 2000;
/** How often an open conversation checks for replies. */
const POLL_MS = 20_000;

const baseOf = (side: MessageSide) => (side === 'BUYER' ? '/messages' : '/seller/messages');

/** A buyer's conversations with stores (/messages), or a store's conversations with buyers (/seller/messages). */
export function MessagesInbox({ side }: { side: MessageSide }) {
  const { data, error, loading } = useAsync(() => api<ConversationSummary[]>(side === 'BUYER' ? '/messages' : '/seller/messages'), [side]);
  return (
    <div className="page page-narrow">
      <div>
        <h1 className="page-title">Messages</h1>
        <p className="page-subtitle">{side === 'BUYER' ? 'Your conversations with the stores you buy from.' : 'Your conversations with buyers.'}</p>
      </div>
      {error && <div className="notice error">{error}</div>}
      {loading && !data ? <div className="loading">Loading…</div> : data && data.length === 0 ? (
        <div className="empty">
          {side === 'BUYER'
            ? <>No messages yet. To ask a store something, use <b>Message the seller</b> on a product or order page.</>
            : <>No messages yet. Buyers can write to you from your product pages and their orders; you can write to a buyer from <Link to="/seller/orders">Orders</Link>.</>}
        </div>
      ) : (
        <ul className="inbox">
          {data?.map((c) => (
            <li key={c.id}>
              <Link to={`${baseOf(side)}/${c.id}`} className={`inbox-row${c.unread ? ' unread' : ''}`}>
                <span className="inbox-with">{c.with}</span>
                <span className="inbox-preview">{c.preview}</span>
                <span className="inbox-when">{dateTime(c.lastMessageAt)}</span>
                {c.unread > 0 && <span className="count-badge" aria-label={`${c.unread} unread`}>{c.unread}</span>}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

/** One conversation, newest at the bottom, with a reply box. Opening it marks it read. */
export function MessageThread({ side }: { side: MessageSide }) {
  const { id } = useParams();
  const { refresh } = useUnread();
  const { data, error, loading, setData, reload } = useAsync(() => api<Conversation>(`/messages/${id}`), [id]);

  useEffect(() => { if (data) refresh(); }, [data, refresh]);
  useEffect(() => {
    const t = window.setInterval(() => { if (document.visibilityState === 'visible') reload(); }, POLL_MS);
    return () => window.clearInterval(t);
  }, [reload]);

  if (error) return <div className="page page-narrow"><div className="notice error">{error}</div><Link to={baseOf(side)}>Back to messages</Link></div>;
  if (loading && !data) return <div className="loading">Loading…</div>;
  if (!data) return null;
  const other = data.side === 'BUYER' ? data.storeName : data.buyerName;

  return (
    <div className="page page-narrow">
      <div className="row" style={{ gap: 20 }}>
        <Link to={baseOf(side)} className="back-btn" aria-label="Back to messages">←</Link>
        <div>
          <h1 className="page-title">{other}</h1>
          {data.side === 'BUYER' && <p className="page-subtitle"><Link to={`/sellers/${data.sellerSlug}`}>Visit the store</Link></p>}
        </div>
      </div>
      <ChatLog conversation={data} />
      {data.canReply
        ? <Composer placeholder={`Write to ${other}…`} conversationId={data.id} onSent={(c) => setData(() => c)} />
        : <div className="notice">This store can't send or receive messages at the moment. The conversation so far is kept here.</div>}
    </div>
  );
}

function ChatLog({ conversation }: { conversation: Conversation }) {
  const end = useRef<HTMLDivElement>(null);
  const count = conversation.messages.length;
  useEffect(() => { end.current?.scrollIntoView({ block: 'nearest' }); }, [count]);
  const orderLink = (orderId: number) => (conversation.side === 'BUYER' ? `/orders/${orderId}` : '/seller/orders');
  return (
    <div className="chat-log" aria-live="polite">
      {conversation.earlier && <div className="muted chat-earlier">Earlier messages aren't shown.</div>}
      {conversation.messages.map((m) => (
        <div key={m.id} className={`chat-msg${m.mine ? ' mine' : ''}`}>
          {(m.product || m.orderId) && (
            <div className="chat-about">
              About {m.product ? <Link to={`/products/${m.product.id}`}>{m.product.name}</Link> : null}
              {m.product && m.orderId ? ' · ' : null}
              {m.orderId ? <Link to={orderLink(m.orderId)}>order #{m.orderId}</Link> : null}
            </div>
          )}
          <div className="chat-bubble">{m.body}</div>
          <div className="chat-meta">{m.mine ? 'You' : m.senderName} · {dateTime(m.createdAt)}</div>
        </div>
      ))}
      <div ref={end} />
    </div>
  );
}

/** The message box. Enter adds a line; the button (or Ctrl/⌘+Enter) sends. */
function Composer({ placeholder, conversationId, onSent, submitLabel = 'Send', post }: {
  placeholder: string;
  conversationId?: number;
  onSent: (c: Conversation) => void;
  submitLabel?: string;
  post?: (body: string) => Promise<Conversation>;
}) {
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function submit(e?: FormEvent) {
    e?.preventDefault();
    const body = text.trim();
    if (!body || busy) return;
    setBusy(true);
    setError('');
    try {
      const c = post ? await post(body) : await api<Conversation>(`/messages/${conversationId}`, { method: 'POST', body: { body } });
      setText('');
      onSent(c);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Your message could not be sent.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="composer" onSubmit={submit}>
      <textarea className="rounded-input" value={text} onChange={(e) => setText(e.target.value)} placeholder={placeholder} maxLength={MAX}
        aria-label="Your message" rows={3}
        onKeyDown={(e) => { if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) void submit(); }} />
      <div className="row-wrap">
        <span className="muted" style={{ fontSize: 12 }}>{text.length > MAX - 200 ? `${MAX - text.length} characters left` : 'Ctrl+Enter to send'}</span>
        <span className="spacer" />
        <button className="submit-btn" disabled={busy || !text.trim()}>{busy ? 'Sending…' : submitLabel}</button>
      </div>
      {error && <div className="notice error" role="alert">{error}</div>}
    </form>
  );
}

/**
 * Starting a conversation. Buyers come from a product or order page (/messages/new?seller=…&product=… or &order=…);
 * sellers from one of their orders (/seller/messages/new?order=…). An existing conversation just carries on.
 */
export function NewMessage({ side }: { side: MessageSide }) {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const seller = params.get('seller') ?? '';
  const productId = Number(params.get('product')) || null;
  const orderId = Number(params.get('order')) || null;
  const product = useAsync(() => (productId ? api<Product>(`/products/${productId}`) : Promise.resolve(null)), [productId]);

  const missing = side === 'BUYER' ? !seller : !orderId;
  if (missing) return <div className="page page-narrow"><div className="notice error">Start a message from a product or order page.</div></div>;
  const to = side === 'BUYER' ? (product.data?.sellerName ?? 'the seller') : 'the buyer';

  return (
    <div className="page page-narrow">
      <div className="row" style={{ gap: 20 }}>
        <Link to={baseOf(side)} className="back-btn" aria-label="Back to messages">←</Link>
        <h1 className="page-title">Message {to}</h1>
      </div>
      <div className="notice">
        {product.data ? <>About <b>{product.data.name}</b>. </> : null}
        {orderId ? <>About order <b>#{orderId}</b>. </> : null}
        {side === 'BUYER'
          ? 'Only you and the store can read this conversation. Please keep payments and personal details on Pacific.'
          : 'Only you and the buyer can read this conversation.'}
      </div>
      <Composer placeholder="Write your message…" submitLabel="Send message"
        post={(body) => side === 'BUYER'
          ? api<Conversation>('/messages', { method: 'POST', body: { sellerSlug: seller, productId, orderId, body } })
          : api<Conversation>('/seller/messages', { method: 'POST', body: { orderId, body } })}
        onSent={(c) => navigate(`${baseOf(side)}/${c.id}`, { replace: true })} />
    </div>
  );
}
