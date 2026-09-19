/** Gold stars filled to the exact rating (4.3 fills 86%), with the number of ratings beside them. */
export function Stars({ value, count }: { value: number; count?: number }) {
  const label = count === 0 ? 'No ratings yet' : `${value.toFixed(1)} out of 5 stars`;
  return (
    <span className="rating-row" aria-label={label}>
      <span className="stars-fill" style={{ ['--pct' as string]: `${Math.max(0, Math.min(5, value)) * 20}%` }} aria-hidden="true">★★★★★</span>
      {count !== undefined && <span className="rating-count">{count === 0 ? 'No ratings yet' : count.toLocaleString('en-GB')}</span>}
    </span>
  );
}
