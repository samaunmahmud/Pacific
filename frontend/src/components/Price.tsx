import { money } from '../ui/format';

/** Selling price, with the struck-through "was" price and a discount badge when the product is a deal. */
export function Price({ price, listPrice, discountPercent, large }: { price: number; listPrice: number | null; discountPercent: number; large?: boolean }) {
  return (
    <span className="price-line">
      {discountPercent > 0 && <span className="deal-badge">-{discountPercent}%</span>}
      <span className={large ? 'pdp-price' : 'product-price'}>{money(price)}</span>
      {listPrice !== null && discountPercent > 0 && <span className="was-price">{money(listPrice)}</span>}
    </span>
  );
}
