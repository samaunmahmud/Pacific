import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import type { Category, Product } from '../api/types';
import { money } from '../ui/format';

interface Suggestions {
  products: Product[];
  categories: Category[];
}

/**
 * Suggestions under the header search box as the shopper types: matching products (with price) and categories.
 * Arrow keys move through them, Enter opens one, Escape closes the list.
 */
export function useSearchSuggest(q: string) {
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [data, setData] = useState<Suggestions>({ products: [], categories: [] });
  const [active, setActive] = useState(-1);
  const seq = useRef(0);

  useEffect(() => {
    const query = q.trim();
    if (query.length < 2) { setData({ products: [], categories: [] }); return; }
    const n = ++seq.current;
    const t = window.setTimeout(() => {
      api<Suggestions>('/search/suggest', { query: { q: query } })
        .then((d) => { if (n === seq.current) { setData(d); setActive(-1); } })
        .catch(() => {});
    }, 180);
    return () => window.clearTimeout(t);
  }, [q]);

  const items: { key: string; label: string; sub?: string; go: () => void }[] = [
    ...data.products.map((p) => ({ key: `p${p.id}`, label: p.name, sub: money(p.boxPrice ?? p.price), go: () => navigate(`/products/${p.catalogId}`) })),
    ...data.categories.map((c) => ({ key: `c${c.id}`, label: c.name, sub: 'Category', go: () => navigate(`/products?category=${c.slug}`) })),
  ];
  const visible = open && items.length > 0;

  /** Returns true when the key was handled (so the form shouldn't submit). */
  function onKey(e: React.KeyboardEvent): boolean {
    if (!visible) return false;
    if (e.key === 'ArrowDown') { e.preventDefault(); setActive((a) => (a + 1) % items.length); return true; }
    if (e.key === 'ArrowUp') { e.preventDefault(); setActive((a) => (a <= 0 ? items.length - 1 : a - 1)); return true; }
    if (e.key === 'Escape') { setOpen(false); return true; }
    if (e.key === 'Enter' && active >= 0) { e.preventDefault(); items[active].go(); setOpen(false); return true; }
    return false;
  }

  const list = visible ? (
    <ul className="suggest-list" role="listbox" aria-label="Search suggestions">
      {items.map((it, i) => (
        <li key={it.key} id={it.key} role="option" aria-selected={i === active} className={i === active ? 'active' : ''}
          onMouseDown={(e) => { e.preventDefault(); it.go(); setOpen(false); }} onMouseEnter={() => setActive(i)}>
          <span className="suggest-label">{it.label}</span>
          {it.sub && <span className="suggest-sub">{it.sub}</span>}
        </li>
      ))}
    </ul>
  ) : null;

  return { list, onKey, open: () => setOpen(true), close: () => setOpen(false), activeId: active >= 0 ? items[active]?.key : undefined };
}
