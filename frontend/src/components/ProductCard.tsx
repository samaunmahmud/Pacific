import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import type { Product } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { useCart } from '../cart/CartContext';
import { useToast } from '../ui/Toast';
import { Price } from './Price';
import { ProductImage } from './ProductImage';
import { Stars } from './Stars';
import { WishlistButton } from './WishlistButton';

export function ProductCard({ product }: { product: Product }) {
  const { user } = useAuth();
  const { add } = useCart();
  const toast = useToast();
  const navigate = useNavigate();
  const location = useLocation();
  const [busy, setBusy] = useState(false);
  const soldOut = product.stock === 0;

  async function addToCart(e: React.MouseEvent) {
    e.preventDefault();
    if (!user) return navigate('/login', { state: { next: location.pathname + location.search } });
    if (user.role !== 'CUSTOMER') return toast.show('Sign in with a customer account to shop.', 'error');
    setBusy(true);
    try {
      await add(product.id, 1);
      toast.show(`Added ${product.name} to your cart`);
    } catch (err) {
      toast.show(err instanceof Error ? err.message : 'Could not add to cart.', 'error');
    } finally {
      setBusy(false);
    }
  }

  return (
    <Link to={`/products/${product.id}`} className="product-card">
      <div className="card-image">
        <ProductImage imageUrl={product.imageUrl} categoryName={product.category?.name} alt={product.name} />
        <WishlistButton productId={product.id} />
        {product.discountPercent >= 10 && <span className="deal-corner">Deal</span>}
      </div>
      <div className="card-body">
        <h3 className="card-title" title={product.name}>{product.name}</h3>
        <Stars value={product.ratingAvg} count={product.ratingCount} />
        <Price price={product.price} listPrice={product.listPrice} discountPercent={product.discountPercent} />
        <div className="card-meta">
          {soldOut ? <span className="stock-note low">Currently unavailable</span>
            : product.stock <= 5 ? <span className="stock-note low">Only {product.stock} left in stock</span>
            : product.price >= 50 ? <span className="free-delivery">FREE delivery</span> : null}
          <span className="sold-by">Sold by {product.sellerName}</span>
        </div>
        <button className="cart-btn" disabled={soldOut || busy} onClick={addToCart}>
          {soldOut ? 'Out of stock' : busy ? 'Adding…' : 'Add to cart'}
        </button>
      </div>
    </Link>
  );
}
