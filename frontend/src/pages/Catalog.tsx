import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../api/client';
import type { Page, Product } from '../api/types';
import { Pagination } from '../components/Pagination';
import { ProductCard } from '../components/ProductCard';
import { useAsync } from '../ui/useAsync';
import { useCategories } from '../ui/useCategories';

const SORTS: [string, string][] = [
  ['popular', 'Featured (most reviewed)'],
  ['newest', 'Newest arrivals'],
  ['rating', 'Avg. customer review'],
  ['discount', 'Biggest discount'],
  ['price_asc', 'Price: low to high'],
  ['price_desc', 'Price: high to low'],
  ['name', 'Name A–Z'],
];

const PRICE_BANDS: [string, string, string][] = [
  ['Under £25', '', '25'],
  ['£25 to £50', '25', '50'],
  ['£50 to £100', '50', '100'],
  ['£100 to £200', '100', '200'],
  ['£200 & above', '200', ''],
];

const PAGE_SIZE = 24;

/** The product listing with a filter sidebar. With `dealsOnly` it becomes the Today's Deals page. */
export function Catalog({ dealsOnly = false }: { dealsOnly?: boolean }) {
  const [params, setParams] = useSearchParams();
  const q = params.get('q') ?? '';
  const category = params.get('category') ?? '';
  const minPrice = params.get('minPrice') ?? '';
  const maxPrice = params.get('maxPrice') ?? '';
  const minRating = params.get('minRating') ?? '';
  const deals = dealsOnly || params.get('deals') === 'true';
  const sort = params.get('sort') ?? (dealsOnly ? 'discount' : 'popular');
  const page = Number(params.get('page') ?? '0') || 0;

  const [filtersOpen, setFiltersOpen] = useState(false); // only matters on small screens; the sidebar is always shown on wide ones
  const categories = useCategories();
  const products = useAsync(
    () => api<Page<Product>>('/products', { query: { q, category, sort, page, size: PAGE_SIZE, deals: deals ? 'true' : undefined, minPrice, maxPrice, minRating } }),
    [q, category, sort, page, deals, minPrice, maxPrice, minRating],
  );

  function update(next: Record<string, string>) {
    const p = new URLSearchParams(params);
    Object.entries(next).forEach(([k, v]) => (v ? p.set(k, v) : p.delete(k)));
    if (!('page' in next)) p.delete('page'); // any filter change goes back to page 1
    setParams(p);
  }

  const filtered = Boolean(category || minPrice || maxPrice || minRating || (deals && !dealsOnly) || q);
  const heading = q ? `Results for “${q}”` : dealsOnly ? "Today's Deals" : categories.find((c) => c.slug === category)?.name ?? 'All products';
  const total = products.data?.totalItems ?? 0;
  const from = total === 0 ? 0 : page * PAGE_SIZE + 1;
  const to = Math.min(total, (page + 1) * PAGE_SIZE);

  function submitPrice(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    update({ minPrice: String(f.get('min') ?? ''), maxPrice: String(f.get('max') ?? '') });
  }

  return (
    <div className="catalog">
      <button className="filters-toggle" onClick={() => setFiltersOpen((o) => !o)} aria-expanded={filtersOpen}>
        {filtersOpen ? 'Hide filters' : 'Show filters'}{filtered ? ' (active)' : ''}
      </button>
      <aside className={`filters ${filtersOpen ? 'open' : ''}`} aria-label="Filters">
        <section>
          <h3>Department</h3>
          <ul>
            <li><button className={!category ? 'on' : ''} onClick={() => update({ category: '' })}>All departments</button></li>
            {categories.map((c) => (
              <li key={c.id}><button className={category === c.slug ? 'on' : ''} onClick={() => update({ category: c.slug })}>{c.name}</button></li>
            ))}
          </ul>
        </section>

        <section>
          <h3>Customer reviews</h3>
          <ul>
            {[4, 3, 2, 1].map((n) => (
              <li key={n}>
                <button className={minRating === String(n) ? 'on' : ''} onClick={() => update({ minRating: minRating === String(n) ? '' : String(n) })} aria-label={`${n} stars and up`}>
                  <span className="stars-fill sm" style={{ ['--pct' as string]: `${n * 20}%` }} aria-hidden="true">★★★★★</span> &amp; Up
                </button>
              </li>
            ))}
          </ul>
        </section>

        <section>
          <h3>Price</h3>
          <ul>
            {PRICE_BANDS.map(([label, min, max]) => (
              <li key={label}>
                <button className={minPrice === min && maxPrice === max ? 'on' : ''} onClick={() => update({ minPrice: min, maxPrice: max })}>{label}</button>
              </li>
            ))}
          </ul>
          <form className="price-range" onSubmit={submitPrice} key={`${minPrice}-${maxPrice}`}>
            <input name="min" type="number" min="0" step="1" placeholder="£ Min" defaultValue={minPrice} aria-label="Minimum price" />
            <input name="max" type="number" min="0" step="1" placeholder="£ Max" defaultValue={maxPrice} aria-label="Maximum price" />
            <button type="submit">Go</button>
          </form>
        </section>

        {!dealsOnly && (
          <section>
            <h3>Deals</h3>
            <label className="check"><input type="checkbox" checked={deals} onChange={(e) => update({ deals: e.target.checked ? 'true' : '' })} /> Today's Deals</label>
          </section>
        )}

        {filtered && <button className="clear-filters" onClick={() => setParams(new URLSearchParams())}>Clear all filters</button>}
      </aside>

      <section className="results">
        <div className="results-bar">
          <div>
            <h1>{heading}</h1>
            {products.data && <span className="results-count">{total === 0 ? 'No results' : `${from}–${to} of ${total.toLocaleString('en-GB')} result${total === 1 ? '' : 's'}`}</span>}
          </div>
          <label className="sort-by">
            <span>Sort by:</span>
            <select value={sort} onChange={(e) => update({ sort: e.target.value })}>
              {SORTS.map(([v, label]) => <option key={v} value={v}>{label}</option>)}
            </select>
          </label>
        </div>

        {products.error && <div className="notice error">{products.error}</div>}
        {products.loading && !products.data ? <div className="loading">Loading…</div> : products.data && products.data.items.length === 0 ? (
          <div className="empty">
            <p>{dealsOnly ? 'No deals match those filters right now.' : 'No products match your search.'}</p>
            <Link className="submit-btn" to={dealsOnly ? '/deals' : '/products'}>Clear filters</Link>
          </div>
        ) : (
          <div className="product-grid" style={{ opacity: products.loading ? 0.6 : 1 }}>
            {products.data?.items.map((p) => <ProductCard key={p.id} product={p} />)}
          </div>
        )}
        {products.data && <Pagination page={products.data.page} totalPages={products.data.totalPages} onChange={(p) => update({ page: String(p) })} />}
      </section>
    </div>
  );
}
