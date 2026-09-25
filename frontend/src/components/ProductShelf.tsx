import { useRef } from 'react';
import { Link } from 'react-router-dom';
import type { Product } from '../api/types';
import { ProductCard } from './ProductCard';

/** A titled row of product cards that scrolls sideways. products undefined = still loading (placeholders). */
export function ProductShelf({ title, products, more, error }: { title: string; products: Product[] | undefined; more?: string; error?: string }) {
  const row = useRef<HTMLDivElement>(null);
  if (products && products.length === 0 && !error) return null;
  const scroll = (dir: number) => row.current?.scrollBy({ left: dir * row.current.clientWidth * 0.85, behavior: 'smooth' });
  return (
    <section className="shelf" aria-label={title}>
      <div className="shelf-head">
        <h2>{title}</h2>
        {more && <Link to={more} className="see-more">See all</Link>}
      </div>
      {error && <div className="notice error">{error}</div>}
      <div className="shelf-wrap">
        <button className="shelf-arrow left" onClick={() => scroll(-1)} aria-label={`Scroll ${title} left`}>‹</button>
        <div className="shelf-row" ref={row}>
          {(products ?? []).map((p) => <div key={p.id} className="shelf-item"><ProductCard product={p} /></div>)}
          {!products && Array.from({ length: 6 }, (_, n) => <div key={n} className="shelf-item"><div className="product-card skeleton-card" /></div>)}
        </div>
        <button className="shelf-arrow right" onClick={() => scroll(1)} aria-label={`Scroll ${title} right`}>›</button>
      </div>
    </section>
  );
}
