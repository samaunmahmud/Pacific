import { stars } from '../ui/format';

export function Stars({ value, count }: { value: number; count?: number }) {
  return (
    <span className="row" style={{ gap: 6 }} aria-label={count === 0 ? 'No ratings yet' : `${value.toFixed(1)} out of 5 stars`}>
      <span className="star-label" style={{ fontSize: 15 }} aria-hidden="true">
        {stars(value)}
      </span>
      {count !== undefined && (
        <span className="count-label">{count === 0 ? 'No ratings yet' : `${value.toFixed(1)} (${count})`}</span>
      )}
    </span>
  );
}
