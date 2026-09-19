import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../api/client';
import type { Category, Page, Product } from '../api/types';
import { Pagination } from '../components/Pagination';
import { ProductCard } from '../components/ProductCard';
import { useAsync } from '../ui/useAsync';

const SORTS: [string, string][] = [
  ['newest', 'Newest'],
  ['rating', 'Top Rated'],
  ['discount', 'Biggest Discount'],
  ['price_asc', 'Price: Low to High'],
  ['price_desc', 'Price: High to Low'],
  ['name', 'Name A–Z'],
];

/** The product listing. With `dealsOnly` it becomes the Today's Deals page. */
export function Catalog({ dealsOnly = false }: { dealsOnly?: boolean }) {
  const [params, setParams] = useSearchParams();
  const q = params.get('q') ?? '';
  const category = params.get('category') ?? '';
  const sort = params.get('sort') ?? (dealsOnly ? 'discount' : 'newest');
  const page = Number(params.get('page') ?? '0') || 0;

  const categories = useAsync(() => api<Category[]>('/categories'), []);
  const products = useAsync(() => api<Page<Product>>('/products', { query: { q, category, sort, page, size: 12, deals: dealsOnly ? 'true' : undefined } }), [q, category, sort, page, dealsOnly]);

  function update(next: Record<string, string>) {
    const p = new URLSearchParams(params);
    Object.entries(next).forEach(([k, v]) => (v ? p.set(k, v) : p.delete(k)));
    if (!('page' in next)) p.delete('page'); // any filter change goes back to page 1
    setParams(p);
  }

  const heading = q ? `Results for “${q}”` : dealsOnly ? "Today's Deals" : categories.data?.find((c) => c.slug === category)?.name ?? 'All products';

  return (
    <div className="page">
      <div className="row-wrap">
        <div>
          <h1 className="page-title">{heading}</h1>
          {products.data && <p className="page-subtitle">{products.data.totalItems} product{products.data.totalItems === 1 ? '' : 's'}</p>}
        </div>
        <span className="spacer" />
        <label className="row" style={{ gap: 10 }}>
          <span className="muted">Sort by</span>
          <select className="pill-select" value={sort} onChange={(e) => update({ sort: e.target.value })}>
            {SORTS.map(([v, label]) => <option key={v} value={v}>{label}</option>)}
          </select>
        </label>
      </div>

      <div className="row-wrap" role="group" aria-label="Categories">
        <button className={`chip ${!category ? 'active' : ''}`} onClick={() => update({ category: '' })}>All</button>
        {categories.data?.map((c) => (
          <button key={c.id} className={`chip ${category === c.slug ? 'active' : ''}`} onClick={() => update({ category: c.slug })}>{c.name}</button>
        ))}
      </div>

      {products.error && <div className="notice error">{products.error}</div>}
      {products.loading && !products.data ? <div className="loading">Loading…</div> : products.data && products.data.items.length === 0 ? (
        <div className="empty">
          <p>{dealsOnly ? 'No deals right now — check back soon.' : 'No products match your search.'}</p>
          <Link className="submit-btn" to={dealsOnly ? '/deals' : '/products'}>Clear filters</Link>
        </div>
      ) : (
        <div className="product-grid" style={{ opacity: products.loading ? 0.6 : 1 }}>
          {products.data?.items.map((p) => <ProductCard key={p.id} product={p} />)}
        </div>
      )}
      {products.data && <Pagination page={products.data.page} totalPages={products.data.totalPages} onChange={(p) => update({ page: String(p) })} />}
    </div>
  );
}
