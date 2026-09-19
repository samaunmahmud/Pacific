import { money } from '../ui/format';

/** "£34.99" as big pounds with small raised pence, the way shop listings usually show it. */
export function MoneyBig({ amount }: { amount: number }) {
  const [pounds, pence = '00'] = amount.toFixed(2).split('.');
  return (
    <span className="money-big" aria-label={money(amount)}>
      <span className="money-cur" aria-hidden="true">£</span>
      <span className="money-whole" aria-hidden="true">{Number(pounds).toLocaleString('en-GB')}</span>
      <span className="money-pence" aria-hidden="true">{pence}</span>
    </span>
  );
}

/** Selling price, with the struck-through "was" price and a discount badge when the product is a deal. */
export function Price({ price, listPrice, discountPercent, large }: { price: number; listPrice: number | null; discountPercent: number; large?: boolean }) {
  const deal = listPrice !== null && discountPercent > 0;
  return (
    <span className={`price-line ${large ? 'large' : ''}`}>
      {deal && <span className="deal-pct">-{discountPercent}%</span>}
      <MoneyBig amount={price} />
      {deal && <span className="was-price">Was: <s>{money(listPrice)}</s></span>}
    </span>
  );
}
