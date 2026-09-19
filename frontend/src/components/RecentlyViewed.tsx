import { useEffect, useState } from 'react';
import { api } from '../api/client';
import type { Product } from '../api/types';
import { getRecent } from '../ui/recent';
import { ProductCard } from './ProductCard';

/** "Recently viewed" strip, from ids stored in this browser. `exclude` hides the product currently on screen. */
export function RecentlyViewed({ exclude }: { exclude?: number }) {
  const [products, setProducts] = useState<Product[]>([]);

  useEffect(() => {
    const ids = getRecent().filter((id) => id !== exclude).slice(0, 4);
    if (ids.length === 0) {
      setProducts([]);
      return;
    }
    api<Product[]>('/products/batch', { query: { ids: ids.join(',') } })
      .then(setProducts)
      .catch(() => setProducts([]));
  }, [exclude]);

  if (products.length === 0) return null;
  return (
    <section className="stack" style={{ gap: 20 }} aria-labelledby="recent-h">
      <h2 className="section-title" id="recent-h">Recently viewed</h2>
      <div className="product-grid">{products.map((p) => <ProductCard key={p.id} product={p} />)}</div>
    </section>
  );
}
