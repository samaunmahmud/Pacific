export interface Slice {
  label: string;
  value: number;
  color: string;
}

/** Small dependency-free pie chart (replaces the JavaFX PieChart). */
export function PieChart({ slices, size = 260 }: { slices: Slice[]; size?: number }) {
  const total = slices.reduce((s, x) => s + x.value, 0);
  const r = size / 2 - 4;
  const c = size / 2;

  if (total === 0) {
    return <div className="empty" style={{ width: size }}>No Data Available</div>;
  }

  let angle = -Math.PI / 2;
  const paths = slices
    .filter((s) => s.value > 0)
    .map((s) => {
      const share = s.value / total;
      if (share >= 0.9999) return <circle key={s.label} cx={c} cy={c} r={r} fill={s.color} />;
      const start = angle;
      angle += share * Math.PI * 2;
      const x1 = c + r * Math.cos(start), y1 = c + r * Math.sin(start);
      const x2 = c + r * Math.cos(angle), y2 = c + r * Math.sin(angle);
      const large = share > 0.5 ? 1 : 0;
      return <path key={s.label} d={`M${c} ${c} L${x1} ${y1} A${r} ${r} 0 ${large} 1 ${x2} ${y2} Z`} fill={s.color} stroke="#fff" strokeWidth="2" />;
    });

  return (
    <div className="row" style={{ gap: 30, flexWrap: 'wrap' }}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} role="img" aria-label={`Review distribution: ${slices.map((s) => `${s.label} ${s.value}`).join(', ')}`}>
        {paths}
      </svg>
      <div className="legend">
        {slices.map((s) => (
          <div key={s.label}><i style={{ background: s.color }} />{s.label}: <b>{s.value}</b> ({Math.round((s.value / total) * 100)}%)</div>
        ))}
      </div>
    </div>
  );
}
