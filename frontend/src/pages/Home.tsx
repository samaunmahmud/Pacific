import { Link } from 'react-router-dom';
import { api } from '../api/client';
import type { Page, Product } from '../api/types';
import { ProductCard } from '../components/ProductCard';
import { RecentlyViewed } from '../components/RecentlyViewed';
import { useAsync } from '../ui/useAsync';

function Shelf({ title, subtitle, query, more }: { title: string; subtitle: string; query: Record<string, string>; more: string }) {
  const { data, error, loading } = useAsync(() => api<Page<Product>>('/products', { query: { ...query, size: 4 } }), [JSON.stringify(query)]);
  if (data && data.items.length === 0 && !error) return null;
  return (
    <section className="stack" style={{ gap: 20 }}>
      <div className="row">
        <div>
          <h2 className="page-title">{title}</h2>
          <p className="page-subtitle">{subtitle}</p>
        </div>
        <span className="spacer" />
        <Link to={more} className="view-all-link">More</Link>
      </div>
      {error && <div className="notice error">{error}</div>}
      {loading && !data ? <div className="loading">Loading…</div> : (
        <div className="product-grid">{data?.items.map((p) => <ProductCard key={p.id} product={p} />)}</div>
      )}
    </section>
  );
}

export function Home() {
  return (
    <div className="page">
      <Shelf title="Today's Deals," subtitle="Biggest discounts right now" query={{ deals: 'true', sort: 'discount' }} more="/deals" />
      <Shelf title="Top rated products," subtitle="Loved by Pacific customers" query={{ sort: 'rating' }} more="/products?sort=rating" />
      <hr />
      <Shelf title="New arrivals" subtitle="Fresh on the shelves" query={{ sort: 'newest' }} more="/products?sort=newest" />
      <RecentlyViewed />
    </div>
  );
}
