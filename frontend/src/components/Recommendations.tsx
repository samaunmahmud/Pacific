import { useState } from 'react';
import { Link } from 'react-router-dom';
import type { Product } from '../api/types';
import { money } from '../ui/format';
import { ProductImage } from './ProductImage';
import { ProductShelf } from './ProductShelf';

export interface ProductRecommendations {
  boughtTogether: Product[];
  related: Product[];
}

/** What a card's "Add to cart" buys, and at what price (the buy box, with a running deal). */
const buyOf = (p: Product) => ({ id: p.catalogId === p.id ? p.boxProductId : p.id, price: p.deal?.price ?? (p.catalogId === p.id && p.boxPrice != null ? p.boxPrice : p.price), stock: p.catalogId === p.id ? p.boxStock : p.stock });

/**
 * "Frequently bought together": this product and what shoppers most often bought with it, ticked by default, with
 * the total and one button to add them all.
 */
export function FrequentlyBoughtTogether({ current, others, onAddAll }: {
  current: { name: string; imageUrl: string | null; categoryName?: string | null; productId: number; price: number; stock: number };
  others: Product[];
  onAddAll: (productIds: number[]) => Promise<void>;
}) {
  const buyable = others.filter((p) => buyOf(p).stock > 0);
  const [ticked, setTicked] = useState<Set<number>>(() => new Set(buyable.map((p) => p.id)));
  const [busy, setBusy] = useState(false);
  if (buyable.length === 0 || current.stock === 0) return null;
  const chosen = buyable.filter((p) => ticked.has(p.id));
  const total = current.price + chosen.reduce((sum, p) => sum + buyOf(p).price, 0);

  async function addAll() {
    setBusy(true);
    try {
      await onAddAll([current.productId, ...chosen.map((p) => buyOf(p).id)]);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="fbt" aria-labelledby="fbt-h">
      <h2 id="fbt-h">Frequently bought together</h2>
      <div className="fbt-row">
        <span className="fbt-thumb"><ProductImage imageUrl={current.imageUrl} categoryName={current.categoryName} alt={current.name} /></span>
        {buyable.map((p) => (
          <span key={p.id} className="fbt-row">
            <span className="fbt-plus" aria-hidden="true">+</span>
            <Link to={`/products/${p.catalogId}`} className="fbt-thumb" aria-label={p.name}>
              <ProductImage imageUrl={p.imageUrl} categoryName={p.category?.name} alt={p.name} />
            </Link>
          </span>
        ))}
      </div>
      <ul className="fbt-list">
        <li><label><input type="checkbox" checked disabled /> <span><b>This item:</b> {current.name} · <b>{money(current.price)}</b></span></label></li>
        {buyable.map((p) => (
          <li key={p.id}>
            <label>
              <input type="checkbox" checked={ticked.has(p.id)} onChange={() => {
                const next = new Set(ticked);
                if (next.has(p.id)) next.delete(p.id); else next.add(p.id);
                setTicked(next);
              }} />
              <span><Link to={`/products/${p.catalogId}`}>{p.name}</Link> · <b>{money(buyOf(p).price)}</b></span>
            </label>
          </li>
        ))}
      </ul>
      <div className="fbt-total">
        <span>Total price: <b>{money(total)}</b></span>
        <button className="cart-btn" onClick={() => void addAll()} disabled={busy}>
          {busy ? 'Adding…' : chosen.length === 0 ? 'Add to cart' : `Add all ${chosen.length + 1} to cart`}
        </button>
      </div>
    </section>
  );
}

export function RelatedProducts({ products }: { products: Product[] | undefined }) {
  return <ProductShelf title="Related products" products={products} />;
}
