import type { Order, OrderEventType } from '../api/types';
import { dateTime } from '../ui/format';

const LABEL: Record<OrderEventType, string> = {
  AWAITING_PAYMENT: 'Waiting for payment',
  PAYMENT_RECEIVED: 'Payment received',
  PLACED: 'Order placed',
  PROCESSING: 'Being prepared',
  SHIPPED: 'Shipped',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled',
};

/** Who did what and when, oldest first. Older orders that pre-date the timeline simply show when they were placed. */
export function OrderActivity({ order }: { order: Order }) {
  const events = order.timeline.length > 0 ? order.timeline : [{ type: 'PLACED' as const, note: null, at: order.createdAt }];
  return (
    <ol className="activity" aria-label="Order activity">
      {events.map((e, n) => (
        <li key={`${e.type}-${n}`} className={`act-${e.type}`}>
          <span className="act-dot" aria-hidden="true" />
          <div>
            <b>{LABEL[e.type]}</b>
            <span className="act-time">{dateTime(e.at)}</span>
            {e.note && <div className="act-note">{e.note}</div>}
          </div>
        </li>
      ))}
    </ol>
  );
}

/** Carrier and tracking number, with a link to follow the parcel when we know the courier. Nothing if there is none. */
export function TrackingInfo({ order }: { order: Order }) {
  if (!order.trackingNumber) return null;
  return (
    <div className="tracking-box">
      <div>
        <span className="muted">{order.trackingCarrier ?? 'Tracking number'}</span>
        <b>{order.trackingNumber}</b>
      </div>
      {order.trackingUrl && <a className="side-btn" href={order.trackingUrl} target="_blank" rel="noopener noreferrer">Track parcel</a>}
    </div>
  );
}
