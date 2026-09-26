import { useState, type FormEvent } from 'react';
import { Link, useParams, useSearchParams, useNavigate } from 'react-router-dom';
import { api } from '../../api/client';
import { CONDITION_LABELS, type Category, type ItemCondition, type Page, type Product, type ProductInput } from '../../api/types';
import { Pagination } from '../../components/Pagination';
import { ProductImage } from '../../components/ProductImage';
import { MorePhotosField } from '../../components/MorePhotosField';
import { PhotoField } from '../../components/PhotoField';
import { VariationsPanel } from '../../components/VariationsPanel';
import { money } from '../../ui/format';
import { useToast } from '../../ui/Toast';
import { useAsync } from '../../ui/useAsync';

function CategoryManager({ categories, onChanged }: { categories: Category[]; onChanged: () => void }) {
  const toast = useToast();
  const [name, setName] = useState('');

  async function run(fn: () => Promise<unknown>, done: string) {
    try {
      await fn();
      toast.show(done);
      onChanged();
    } catch (e) {
      toast.show(e instanceof Error ? e.message : 'Action failed.', 'error');
    }
  }

  return (
    <details className="square-review-box static">
      <summary style={{ cursor: 'pointer', fontWeight: 'bold' }}>Manage categories ({categories.length})</summary>
      <div className="stack" style={{ marginTop: 15 }}>
        <div className="row-wrap">
          {categories.map((c) => (
            <span key={c.id} className="chip" style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}>
              {c.name}
              <button className="report-link" title="Rename" aria-label={`Rename ${c.name}`} onClick={() => { const n = window.prompt('New name', c.name); if (n?.trim()) void run(() => api(`/admin/categories/${c.id}`, { method: 'PUT', body: { name: n.trim() } }), 'Category renamed'); }}>✎</button>
              <button className="report-link" title="Delete" aria-label={`Delete ${c.name}`} onClick={() => window.confirm(`Delete "${c.name}"? Its products stay, but become uncategorised.`) && run(() => api(`/admin/categories/${c.id}`, { method: 'DELETE' }), 'Category deleted')}>✕</button>
            </span>
          ))}
        </div>
        <form className="row" onSubmit={(e) => { e.preventDefault(); if (name.trim()) void run(async () => { await api('/admin/categories', { method: 'POST', body: { name: name.trim() } }); setName(''); }, 'Category added'); }}>
          <input className="rounded-input" style={{ maxWidth: 280 }} placeholder="New category name" value={name} onChange={(e) => setName(e.target.value)} maxLength={80} aria-label="New category name" />
          <button className="submit-btn" disabled={!name.trim()}>Add</button>
        </form>
      </div>
    </details>
  );
}

export function StockCell({ product, onSaved, base = '/admin/products' }: { product: Product; onSaved: () => void; base?: string }) {
  const toast = useToast();
  const [value, setValue] = useState(String(product.stock));
  const changed = value !== String(product.stock);

  async function save() {
    const n = Number(value);
    if (!Number.isInteger(n) || n < 0) return toast.show('Stock must be a whole number, 0 or more.', 'error');
    try {
      await api(`${base}/${product.id}/stock`, { method: 'PATCH', body: { stock: n } });
      toast.show('Stock updated');
      onSaved();
    } catch (e) {
      toast.show(e instanceof Error ? e.message : 'Could not update stock.', 'error');
    }
  }

  return (
    <span className="row" style={{ gap: 6 }}>
      <input className="num-input" type="number" min={0} value={value} onChange={(e) => setValue(e.target.value)} aria-label={`Stock for ${product.name}`} />
      {changed && <button className="mini-icon-btn" onClick={save} title="Save stock" aria-label="Save stock">✓</button>}
    </span>
  );
}

export function AdminProducts() {
  const [params, setParams] = useSearchParams();
  const q = params.get('q') ?? '';
  const page = Number(params.get('page') ?? '0') || 0;
  const toast = useToast();

  const categories = useAsync(() => api<Category[]>('/categories'), []);
  const products = useAsync(() => api<Page<Product>>('/admin/products', { query: { q, page, size: 15 } }), [q, page]);

  async function toggle(p: Product) {
    try {
      if (p.active) await api(`/admin/products/${p.id}`, { method: 'DELETE' });
      else await api(`/admin/products/${p.id}`, { method: 'PUT', body: toInput(p, { active: true }) });
      toast.show(p.active ? 'Product hidden from the storefront' : 'Product is live again');
      products.reload();
    } catch (e) {
      toast.show(e instanceof Error ? e.message : 'Action failed.', 'error');
    }
  }

  return (
    <div className="page">
      <div className="row-wrap">
        <div>
          <h1 className="page-title">Products</h1>
          {products.data && <p className="page-subtitle">{products.data.totalItems} product{products.data.totalItems === 1 ? '' : 's'}{q && ` matching “${q}”`}</p>}
        </div>
        <span className="spacer" />
        <Link to="/admin/products/new" className="submit-btn">+ Add product</Link>
      </div>

      <CategoryManager categories={categories.data ?? []} onChanged={() => { categories.reload(); products.reload(); }} />

      {products.error && <div className="notice error">{products.error}</div>}
      <div className="square-review-box static table-wrap">
        <table className="data">
          <thead><tr><th>Product</th><th>Seller</th><th>Category</th><th>Price</th><th>Stock</th><th>Rating</th><th>Status</th><th></th></tr></thead>
          <tbody>
            {products.data?.items.map((p) => (
              <tr key={p.id} style={{ opacity: p.active ? 1 : 0.6 }}>
                <td><span className="row"><span className="thumb"><ProductImage imageUrl={p.imageUrl} categoryName={p.category?.name} alt="" /></span><span><b>{p.name}</b>{p.variation && <span className="variation-label">{p.variation}</span>}</span>{p.catalogId !== p.id && <span className="chip small">Offer</span>}</span></td>
                <td>{p.sellerName}</td>
                <td>{p.category?.name ?? <span className="muted">—</span>}</td>
                <td>{money(p.price)}{p.discountPercent > 0 && <span className="deal-badge" style={{ marginLeft: 6 }}>-{p.discountPercent}%</span>}</td>
                <td><StockCell key={`${p.id}-${p.stock}`} product={p} onSaved={products.reload} /></td>
                <td>{p.ratingCount ? `${p.ratingAvg.toFixed(1)} ★ (${p.ratingCount})` : <span className="muted">—</span>}</td>
                <td>{p.active ? <span className="badge ok">Live</span> : <span className="badge warn">Hidden</span>}</td>
                <td><span className="row" style={{ gap: 8 }}>
                  <Link to={`/admin/products/${p.id}`} className="mini-icon-btn" style={{ textDecoration: 'none', color: 'inherit' }} title="Edit" aria-label={`Edit ${p.name}`}>✎</Link>
                  <button className={`mini-icon-btn ${p.active ? 'delete-btn' : ''}`} onClick={() => toggle(p)} title={p.active ? 'Hide from storefront' : 'Show on storefront'} aria-label={p.active ? `Hide ${p.name}` : `Show ${p.name}`}>{p.active ? '🗑' : '↺'}</button>
                </span></td>
              </tr>
            ))}
          </tbody>
        </table>
        {products.data && products.data.items.length === 0 && <div className="empty">No products found.</div>}
      </div>
      {products.data && <Pagination page={products.data.page} totalPages={products.data.totalPages} onChange={(p) => setParams({ ...(q ? { q } : {}), page: String(p) })} />}
    </div>
  );
}

export function toInput(p: Product, override: Partial<ProductInput> = {}): ProductInput {
  return { name: p.name, description: p.description ?? '', price: p.price, listPrice: p.listPrice, stock: p.stock, imageUrl: p.imageUrl ?? '', moreImages: p.moreImages ?? [], categoryId: p.category?.id ?? null, active: p.active, condition: p.condition, ...override };
}

/**
 * Create or edit a product. Admins manage Pacific's own catalogue at /admin/products; sellers manage their own
 * listings at /seller/products. Same form, different API prefix.
 */
export function ProductFormPage({ scope }: { scope: 'admin' | 'seller' }) {
  // A fresh form per product: the variations panel links from one product's form to another's.
  const { id } = useParams();
  return <ProductForm key={id ?? 'new'} scope={scope} />;
}

function ProductForm({ scope }: { scope: 'admin' | 'seller' }) {
  const base = scope === 'admin' ? '/admin/products' : '/seller/products';
  const { id } = useParams();
  const editing = id !== undefined;
  const navigate = useNavigate();
  const toast = useToast();
  const categories = useAsync(() => api<Category[]>('/categories'), []);
  const existing = useAsync(() => (editing ? api<Product>(`${base}/${id}`) : Promise.resolve(null)), [id]);

  const [form, setForm] = useState<ProductInput | null>(editing ? null : { name: '', description: '', price: 0, listPrice: null, stock: 0, imageUrl: '', moreImages: [], categoryId: null, active: true });
  const [priceText, setPriceText] = useState('');
  const [listText, setListText] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [uploadingMain, setUploading] = useState(false);
  const [uploadingMore, setUploadingMore] = useState(false);
  const uploading = uploadingMain || uploadingMore;

  if (editing && existing.data && !form) {
    setForm(toInput(existing.data));
    setPriceText(existing.data.price.toFixed(2));
    setListText(existing.data.listPrice ? existing.data.listPrice.toFixed(2) : '');
  }
  if (existing.error && !existing.data) return <div className="page page-narrow"><div className="notice error">{existing.error}</div></div>;
  if (!form) return <div className="loading">Loading…</div>;

  // From the latest state: uploads finish after later edits and must not undo them.
  // Another seller's product page: the details come from that page, so only this listing's terms are editable.
  const isOffer = !!existing.data && existing.data.catalogId !== existing.data.id;
  const set = <K extends keyof ProductInput>(k: K, v: ProductInput[K]) => setForm((f) => f && { ...f, [k]: v });

  /** Swaps the main photo with extra photo {@code i} (or just promotes it when there's no main photo). */
  const makeMain = (i: number) => setForm((f) => {
    if (!f) return f;
    const more = [...f.moreImages];
    const [chosen] = more.splice(i, 1, f.imageUrl.trim());
    return { ...f, imageUrl: chosen, moreImages: more.filter((u) => u !== '') };
  });

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!form) return;
    if (!/^\d{1,8}(\.\d{1,2})?$/.test(priceText.trim())) return setError('Enter a price like 19.99.');
    const price = Number(priceText);
    const listTrim = listText.trim();
    if (listTrim && (!/^\d{1,8}(\.\d{1,2})?$/.test(listTrim) || Number(listTrim) <= price)) {
      return setError('The "was" price must be a price like 24.99 that is higher than the selling price (or leave it empty).');
    }
    setBusy(true);
    setError('');
    try {
      const body = { ...form, price, listPrice: listTrim ? Number(listTrim) : null, imageUrl: form.imageUrl.trim() || null, description: form.description.trim() || null };
      await api(editing ? `${base}/${id}` : base, { method: editing ? 'PUT' : 'POST', body });
      toast.show(editing ? 'Product saved' : 'Product created');
      navigate(base);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not save the product.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="page page-narrow">
    <form style={{ display: 'contents' }} onSubmit={submit}>
      <div className="row" style={{ gap: 20 }}>
        <Link to={base} className="back-btn" aria-label="Back to products">←</Link>
        <h1 className="page-title">{isOffer ? 'Edit your offer' : editing ? 'Edit product' : 'Add product'}</h1>
      </div>
      {isOffer && existing.data && (
        <div className="notice">
          You sell <b>{existing.data.name}</b> on its <Link to={`/products/${existing.data.catalogId}`}>shared product page</Link>.
          The name, photos and description come from that page; set your own price, stock and condition here.
        </div>
      )}
      <div className="square-review-box static stack">
        <div className="form-grid">
          {!isOffer && <div className="form-field full"><label className="field-label small" htmlFor="pn">Name</label><input id="pn" className="rounded-input" value={form.name} onChange={(e) => set('name', e.target.value)} required maxLength={160} /></div>}
          {!isOffer && <div className="form-field full"><label className="field-label small" htmlFor="pd">Description</label><textarea id="pd" className="rounded-input" value={form.description} onChange={(e) => set('description', e.target.value)} maxLength={2000} /></div>}
          <div className="form-field"><label className="field-label small" htmlFor="pp">Price (£)</label><input id="pp" className="rounded-input" inputMode="decimal" value={priceText} onChange={(e) => setPriceText(e.target.value)} required /></div>
          <div className="form-field"><label className="field-label small" htmlFor="pl">"Was" price (£, optional — makes it a deal)</label><input id="pl" className="rounded-input" inputMode="decimal" value={listText} onChange={(e) => setListText(e.target.value)} placeholder="e.g. 29.99" /></div>
          <div className="form-field"><label className="field-label small" htmlFor="ps">Stock</label><input id="ps" className="rounded-input" type="number" min={0} value={form.stock} onChange={(e) => set('stock', Math.max(0, Math.floor(Number(e.target.value) || 0)))} required /></div>
          {isOffer && (
            <div className="form-field"><label className="field-label small" htmlFor="pcond">Condition</label>
              <select id="pcond" className="rounded-input" value={form.condition ?? 'NEW'} onChange={(e) => set('condition', e.target.value as ItemCondition)}>
                {(Object.keys(CONDITION_LABELS) as ItemCondition[]).map((c) => <option key={c} value={c}>{CONDITION_LABELS[c]}</option>)}
              </select>
            </div>
          )}
          {!isOffer && <div className="form-field"><label className="field-label small" htmlFor="pc">Category</label>
            <select id="pc" className="rounded-input" value={form.categoryId ?? ''} onChange={(e) => set('categoryId', e.target.value ? Number(e.target.value) : null)}>
              <option value="">Uncategorised</option>
              {categories.data?.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          </div>}
          <div className="form-field"><label className="field-label small" htmlFor="pa">Visibility</label>
            <select id="pa" className="rounded-input" value={form.active ? 'live' : 'hidden'} onChange={(e) => set('active', e.target.value === 'live')}>
              <option value="live">Live on the storefront</option>
              <option value="hidden">Hidden</option>
            </select>
          </div>
          {!isOffer && <>
          <div className="form-field full"><span className="field-label small">Photo (optional)</span>
            <PhotoField value={form.imageUrl} onChange={(url) => set('imageUrl', url)} onBusyChange={setUploading} name={form.name}
              categoryName={categories.data?.find((c) => c.id === form.categoryId)?.name} />
          </div>
          <div className="form-field full"><span className="field-label small">More photos (optional, shown on the product page)</span>
            <MorePhotosField value={form.moreImages} onChange={(urls) => set('moreImages', urls)} onMakeMain={makeMain} onBusyChange={setUploadingMore}
              name={form.name} categoryName={categories.data?.find((c) => c.id === form.categoryId)?.name} />
          </div>
          </>}
        </div>
        {error && <div className="notice error" role="alert">{error}</div>}
        <div className="row" style={{ justifyContent: 'flex-end' }}>
          <button className="submit-btn" disabled={busy || uploading}>{busy ? 'Saving…' : uploading ? 'Uploading photo…' : 'Save product'}</button>
        </div>
      </div>
    </form>
      {existing.data && !isOffer && (
        <VariationsPanel key={`${existing.data.variations?.familyId ?? 'none'}-${existing.data.variation ?? ''}`} base={base} product={existing.data} onChanged={existing.reload} />
      )}
    </div>
  );
}

export const AdminProductForm = () => <ProductFormPage scope="admin" />;
