import { Link } from 'react-router-dom';
import { api } from '../api/client';
import type { Product } from '../api/types';
import { ProductCard } from '../components/ProductCard';
import { useAsync } from '../ui/useAsync';
import { useWishlist } from '../cart/WishlistContext';

export function WishlistPage() {
  const { ids } = useWishlist();
  // refetch when items are added/removed (the heart on a card changes the id set)
  const { data, error, loading } = useAsync(() => api<Product[]>('/wishlist'), [ids.size]);
  return (
    <div className="page">
      <div>
        <h1 className="page-title">Your Wish List</h1>
        <p className="page-subtitle">Things you're saving for later</p>
      </div>
      {error && <div className="notice error">{error}</div>}
      {loading && !data ? <div className="loading">Loading…</div> : data && data.length === 0 ? (
        <div className="empty"><p>Nothing saved yet. Tap the ♡ on any product to keep it here.</p><Link className="submit-btn" to="/products">Browse products</Link></div>
      ) : (
        <div className="product-grid">{data?.map((p) => <ProductCard key={p.id} product={p} />)}</div>
      )}
    </div>
  );
}
