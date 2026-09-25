const gbp = new Intl.NumberFormat('en-GB', { style: 'currency', currency: 'GBP' });

export const money = (n: number) => gbp.format(n);

export const dateTime = (iso: string) =>
  new Date(iso).toLocaleString('en-GB', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });

export const dateOnly = (iso: string) =>
  new Date(iso).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });

export const stars = (rating: number) => {
  const r = Math.max(0, Math.min(5, Math.round(rating)));
  return '★'.repeat(r) + '☆'.repeat(5 - r);
};

export function minutesLeft(iso: string | null): number {
  if (!iso) return 0;
  return Math.max(0, Math.ceil((new Date(iso).getTime() - Date.now()) / 60000));
}

/** "AWAITING_PAYMENT" -> "Awaiting payment" */
export const statusLabel = (status: string) => (status.charAt(0) + status.slice(1).toLowerCase()).replace(/_/g, ' ');

/** A delivery date (YYYY-MM-DD, no time zone) as "Thursday 1 October", or "Thu 1 Oct" when short. */
export function deliveryDay(isoDate: string, short = false): string {
  const [y, m, d] = isoDate.split('-').map(Number);
  return new Date(y, m - 1, d).toLocaleDateString('en-GB', short
    ? { weekday: 'short', day: 'numeric', month: 'short' }
    : { weekday: 'long', day: 'numeric', month: 'long' });
}

/** "Thursday 1 October" or "Thu 1 Oct – Fri 2 Oct" for a range. */
export function deliveryRange(from: string, to: string | null | undefined): string {
  return !to || to === from ? deliveryDay(from) : `${deliveryDay(from, true)} – ${deliveryDay(to, true)}`;
}

/** "3 hrs 12 mins" until the order cut-off, or null once it's passed. */
export function timeLeft(untilIso: string | null, now = Date.now()): string | null {
  if (!untilIso) return null;
  const mins = Math.floor((new Date(untilIso).getTime() - now) / 60000);
  if (mins <= 0) return null;
  const h = Math.floor(mins / 60), m = mins % 60;
  return h > 0 ? `${h} hr${h === 1 ? '' : 's'} ${m} min${m === 1 ? '' : 's'}` : `${m} min${m === 1 ? '' : 's'}`;
}
