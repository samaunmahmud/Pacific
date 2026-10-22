import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import type { DailySales, PeriodTotals, SellerInsights } from '../api/types';
import { money } from '../ui/format';
import { useAsync } from '../ui/useAsync';

const SPANS = [7, 30, 90] as const;
type Span = (typeof SPANS)[number];

const shortDay = (iso: string) =>
  new Date(`${iso}T12:00:00`).toLocaleDateString('en-GB', { day: 'numeric', month: 'short' });

/** "+12%" against the span before; "new" when there was nothing before; null when both are zero. */
export function change(now: number, before: number): { label: string; tone: 'up' | 'down' | 'flat' } | null {
  if (now === 0 && before === 0) return null;
  if (before === 0) return { label: 'new', tone: 'up' };
  const pct = Math.round(((now - before) / before) * 100);
  if (pct === 0) return { label: '0%', tone: 'flat' };
  return { label: `${pct > 0 ? '+' : ''}${pct}%`, tone: pct > 0 ? 'up' : 'down' };
}

function Delta({ now, before, days }: { now: number; before: number; days: number }) {
  const c = change(now, before);
  if (!c) return <span className="ins-delta flat">no sales yet</span>;
  const arrow = c.tone === 'up' ? '▲' : c.tone === 'down' ? '▼' : '•';
  return <span className={`ins-delta ${c.tone}`} title={`Compared with the ${days} days before`}>{arrow} {c.label} <span className="ins-vs">vs previous {days} days</span></span>;
}

function Figures({ cur, prev, days }: { cur: PeriodTotals; prev: PeriodTotals; days: number }) {
  const tiles = [
    { label: 'Sales', value: money(cur.sales), now: cur.sales, before: prev.sales },
    { label: 'Orders', value: String(cur.orders), now: cur.orders, before: prev.orders },
    { label: 'Units sold', value: String(cur.units), now: cur.units, before: prev.units },
    { label: 'Average order', value: cur.averageOrder == null ? '–' : money(cur.averageOrder), now: cur.averageOrder ?? 0, before: prev.averageOrder ?? 0 },
  ];
  return (
    <div className="ins-figures">
      {tiles.map((t) => (
        <div key={t.label} className="ins-figure">
          <span className="ins-label">{t.label}</span>
          <span className="ins-value">{t.value}</span>
          <Delta now={t.now} before={t.before} days={days} />
        </div>
      ))}
    </div>
  );
}

/** Takings per day as an area chart; hover or tap a day for its figures. */
function SalesChart({ daily }: { daily: DailySales[] }) {
  const [hover, setHover] = useState<number | null>(null);
  const W = 720, H = 220, L = 8, R = 8, T = 14, B = 26;
  const max = Math.max(...daily.map((d) => d.sales), 0);
  const top = max === 0 ? 10 : niceCeil(max);
  const x = (i: number) => L + (daily.length === 1 ? 0 : (i / (daily.length - 1)) * (W - L - R));
  const y = (v: number) => T + (1 - v / top) * (H - T - B);
  const line = daily.map((d, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)} ${y(d.sales).toFixed(1)}`).join(' ');
  const area = `${line} L${x(daily.length - 1).toFixed(1)} ${H - B} L${x(0).toFixed(1)} ${H - B} Z`;
  const ticks = [0, 0.5, 1].map((f) => top * f);
  const labelAt = [0, Math.floor((daily.length - 1) / 2), daily.length - 1];
  const h = hover == null ? null : daily[hover];

  const pick = (clientX: number, el: SVGSVGElement) => {
    const box = el.getBoundingClientRect();
    const px = ((clientX - box.left) / box.width) * W;
    const i = Math.round(((px - L) / (W - L - R)) * (daily.length - 1));
    setHover(Math.min(daily.length - 1, Math.max(0, i)));
  };

  return (
    <div className="ins-chart">
      <div className="ins-axis-labels" aria-hidden="true">
        {ticks.slice().reverse().map((t) => <span key={t}>{money(t).replace(/\.00$/, '')}</span>)}
      </div>
      <div className="ins-plot">
        <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" role="img"
          aria-label={`Sales per day from ${shortDay(daily[0].date)} to ${shortDay(daily[daily.length - 1].date)}, highest ${money(max)}`}
          onMouseMove={(e) => pick(e.clientX, e.currentTarget)} onMouseLeave={() => setHover(null)}
          onTouchStart={(e) => pick(e.touches[0].clientX, e.currentTarget)}>
          <defs>
            <linearGradient id="ins-fill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="var(--brand-ink)" stopOpacity="0.28" />
              <stop offset="100%" stopColor="var(--brand-ink)" stopOpacity="0" />
            </linearGradient>
          </defs>
          {ticks.map((t) => <line key={t} className="ins-grid" x1={L} x2={W - R} y1={y(t)} y2={y(t)} vectorEffect="non-scaling-stroke" />)}
          <path d={area} fill="url(#ins-fill)" />
          <path d={line} className="ins-line" vectorEffect="non-scaling-stroke" />
          {hover != null && <line className="ins-cursor" x1={x(hover)} x2={x(hover)} y1={T} y2={H - B} vectorEffect="non-scaling-stroke" />}
        </svg>
        {h && hover != null && (
          <>
            <span className="ins-dot" style={{ left: `${(x(hover) / W) * 100}%`, top: `${(y(h.sales) / H) * 100}%` }} aria-hidden="true" />
            <div className={`ins-tip ${hover > daily.length / 2 ? 'left' : ''}`} style={{ left: `${(x(hover) / W) * 100}%` }}>
              <b>{shortDay(h.date)}</b>
              <span>{money(h.sales)}</span>
              <span className="ins-vs">{h.orders} order{h.orders === 1 ? '' : 's'}</span>
            </div>
          </>
        )}
        <div className="ins-x" aria-hidden="true">
          {labelAt.map((i, k) => <span key={k} style={{ left: `${(x(i) / W) * 100}%` }}>{shortDay(daily[i].date)}</span>)}
        </div>
      </div>
    </div>
  );
}

/** 1, 2 or 5 times a power of ten, at or above n: a round top for the chart's scale. */
function niceCeil(n: number) {
  const p = 10 ** Math.floor(Math.log10(n));
  return [1, 2, 5, 10].map((m) => m * p).find((v) => v >= n) ?? 10 * p;
}

/** Seller Central's sales panel: figures with their change, the daily chart and the best sellers. */
export function SalesInsights() {
  const [days, setDays] = useState<Span>(30);
  const { data, error } = useAsync(() => api<SellerInsights>(`/seller/insights?days=${days}`), [days]);
  const best = data?.topProducts[0]?.revenue ?? 0;

  return (
    <section className="ins-panel" aria-labelledby="ins-title">
      <header className="ins-head">
        <div>
          <h2 id="ins-title">Sales</h2>
          <p className="ins-sub">Orders placed, excluding cancelled ones · UK time</p>
        </div>
        <div className="ins-seg" role="group" aria-label="Period">
          {SPANS.map((d) => (
            <button key={d} type="button" className={d === days ? 'on' : ''} aria-pressed={d === days} onClick={() => setDays(d)}>{d} days</button>
          ))}
        </div>
      </header>
      {error && <div className="notice error">{error}</div>}
      {!data ? <div className="ins-loading skeleton" aria-label="Loading sales" /> : (
        <>
          <Figures cur={data.current} prev={data.previous} days={data.days} />
          <div className="ins-body">
            <SalesChart daily={data.daily} />
            <div className="ins-top">
              <h3>Best sellers</h3>
              {data.topProducts.length === 0 ? (
                <p className="ins-empty">No sales in the last {data.days} days yet. <Link to="/seller/promotions">Run a promotion</Link> to get things moving.</p>
              ) : (
                <ol>
                  {data.topProducts.map((p, i) => (
                    <li key={p.productId}>
                      <span className="ins-rank">{i + 1}</span>
                      <div className="ins-prod">
                        <Link to={`/seller/products/${p.productId}`}>{p.name}</Link>
                        <span className="ins-bar"><i style={{ width: `${best ? (p.revenue / best) * 100 : 0}%` }} /></span>
                      </div>
                      <span className="ins-amt">{money(p.revenue)}<small>{p.units} sold</small></span>
                    </li>
                  ))}
                </ol>
              )}
            </div>
          </div>
        </>
      )}
    </section>
  );
}
