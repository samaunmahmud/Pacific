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
  // A product page's card sells its buy box, which may be another seller's listing; a store's own listing sells itself.
  const isPage = product.catalogId === product.id;
  const buyId = isPage ? product.boxProductId : product.id;
  const stock = isPage ? product.boxStock : product.stock;
  const price = isPage && product.boxPrice != null ? product.boxPrice : product.price;
  const ownPriceShown = price === product.price;
  const soldOut = stock === 0;

  async function addToCart(e: React.MouseEvent) {
    e.preventDefault();
    if (!user) return navigate('/login', { state: { next: location.pathname + location.search } });
    if (user.role !== 'CUSTOMER') return toast.show('Sign in with a customer account to shop.', 'error');
    setBusy(true);
    try {
      await add(buyId, 1);
      toast.show(`Added ${product.name} to your cart`);
    } catch (err) {
      toast.show(err instanceof Error ? err.message : 'Could not add to cart.', 'error');
    } finally {
      setBusy(false);
    }
  }

  return (
    <Link to={`/products/${product.catalogId}`} className="product-card">
      <div className="card-image">
        <ProductImage imageUrl={product.imageUrl} categoryName={product.category?.name} alt={product.name} />
        <WishlistButton productId={product.catalogId} />
        {ownPriceShown && product.discountPercent >= 10 && <span className="deal-corner">Deal</span>}
      </div>
      <div className="card-body">
        <h3 className="card-title" title={product.name}>{product.name}</h3>
        <Stars value={product.ratingAvg} count={product.ratingCount} />
        <Price price={price} listPrice={ownPriceShown ? product.listPrice : null} discountPercent={ownPriceShown ? product.discountPercent : 0} />
        <div className="card-meta">
          {soldOut ? <span className="stock-note low">Currently unavailable</span>
            : stock <= 5 ? <span className="stock-note low">Only {stock} left in stock</span>
            : price >= 50 ? <span className="free-delivery">FREE delivery</span> : null}
          <span className="sold-by">Sold by {isPage ? product.boxSellerName : product.sellerName}</span>
          {isPage && product.offerCount > 1 && <span className="more-offers">+{product.offerCount - 1} other seller{product.offerCount > 2 ? 's' : ''}</span>}
        </div>
        <button className="cart-btn" disabled={soldOut || busy} onClick={addToCart}>
          {soldOut ? 'Out of stock' : busy ? 'Adding…' : 'Add to cart'}
        </button>
      </div>
    </Link>
  );
}
