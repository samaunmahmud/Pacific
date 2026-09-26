import { useEffect, useState } from 'react';
import type { Coupon, Deal } from '../api/types';
import { money } from '../ui/format';

/** "2h 14m" until an instant, ticking every 30 seconds; null once it's passed. */
export function useCountdown(untilIso: string | null | undefined): string | null {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!untilIso) return;
    const t = window.setInterval(() => setNow(Date.now()), 30_000);
    return () => window.clearInterval(t);
  }, [untilIso]);
  if (!untilIso) return null;
  const mins = Math.ceil((new Date(untilIso).getTime() - now) / 60000);
  if (mins <= 0) return null;
  const h = Math.floor(mins / 60), m = mins % 60;
  return h > 0 ? `${h}h ${m}m` : `${m}m`;
}

/** A Lightning Deal: its badge, how long is left and how much has been claimed. */
export function DealBadge({ deal, compact = false }: { deal: Deal; compact?: boolean }) {
  const left = useCountdown(deal.endsAt);
  if (!left) return null;
  return (
    <div className={`deal-block${compact ? ' compact' : ''}`}>
      <div className="deal-head">
        <span className="deal-tag">Lightning Deal</span>
        <span className="deal-off">-{deal.percentOff}%</span>
        <span className="deal-ends">{deal.soldOut ? 'Deal claimed' : `Ends in ${left}`}</span>
      </div>
      <div className="deal-bar" role="img" aria-label={`${deal.percentClaimed}% claimed`}>
        <span style={{ width: `${Math.min(deal.percentClaimed, 100)}%` }} />
      </div>
      {!compact && <div className="deal-claimed">{deal.percentClaimed}% claimed · was {money(deal.regularPrice)}</div>}
    </div>
  );
}

/** "Save 10% with coupon" (on cards) or "Coupon applied" once clipped. */
export function CouponTag({ coupon }: { coupon: Coupon }) {
  return <span className={`coupon-tag${coupon.clipped ? ' clipped' : ''}`}>{coupon.clipped ? `✓ ${coupon.percentOff}% coupon applied` : `Save ${coupon.percentOff}% with coupon`}</span>;
}
