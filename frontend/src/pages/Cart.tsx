import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useCart } from '../cart/CartContext';
import { ProductImage } from '../components/ProductImage';
import { money } from '../ui/format';
import { useToast } from '../ui/Toast';

export function CartPage() {
  const { cart, setQuantity, remove } = useCart();
  const navigate = useNavigate();
  const toast = useToast();
  const [busyId, setBusyId] = useState<number | null>(null);

  async function run(id: number, fn: () => Promise<void>) {
    setBusyId(id);
    try {
      await fn();
    } catch (e) {
      toast.show(e instanceof Error ? e.message : 'Could not update your cart.', 'error');
    } finally {
      setBusyId(null);
    }
  }

  if (!cart) return <div className="loading">Loading…</div>;

  if (cart.items.length === 0) {
    return (
      <div className="page page-narrow">
        <h1 className="page-title">Your cart</h1>
        <div className="empty">
          <p>Your cart is empty.</p>
          <Link className="submit-btn" to="/products">Start shopping</Link>
        </div>
      </div>
    );
  }

  return (
    <div className="page">
      <h1 className="page-title">Your cart</h1>
      <div className="two-col">
        <div className="stack">
          {cart.shipments.map((ship) => {
            const toFree = Math.max(0, cart.freeShippingThreshold - ship.subtotal);
            return (
              <div key={ship.sellerName} className="square-review-box static">
                <div className="row-wrap" style={{ marginBottom: 12 }}>
                  <span>Sold and shipped by{' '}
                    {ship.sellerSlug ? <Link to={`/sellers/${ship.sellerSlug}`} style={{ fontWeight: 'bold' }}>{ship.sellerName}</Link> : <b>{ship.sellerName}</b>}
                  </span>
                  <span className="spacer" />
                  <span className="muted">Shipping: {ship.shipping === 0 ? 'FREE' : money(ship.shipping)}</span>
                </div>
                {cart.items.filter((i) => i.sellerName === ship.sellerName).map((i) => (
                  <div key={i.productId} className="cart-line">
                    <Link to={`/products/${i.productId}`} className="thumb" style={{ width: 90, height: 90 }} aria-label={i.name}>
                      <ProductImage imageUrl={i.imageUrl} categoryName={i.categoryName} alt={i.name} />
                    </Link>
                    <div>
                      <Link to={`/products/${i.productId}`} className="product-title-text" style={{ textDecoration: 'none' }}>{i.name}</Link>
                      <div className="muted">{money(i.unitPrice)} each</div>
                      {i.quantity > i.stock && <div className="error-text">Only {i.stock} left in stock</div>}
                      <button className="report-link" onClick={() => run(i.productId, () => remove(i.productId))} disabled={busyId === i.productId}>Remove</button>
                    </div>
                    <div className="qty" aria-label={`Quantity of ${i.name}`}>
                      <button onClick={() => run(i.productId, () => setQuantity(i.productId, i.quantity - 1))} disabled={busyId === i.productId} aria-label="Decrease">−</button>
                      <span>{i.quantity}</span>
                      <button onClick={() => run(i.productId, () => setQuantity(i.productId, i.quantity + 1))} disabled={busyId === i.productId || i.quantity >= Math.min(i.stock, 10)} aria-label="Increase">+</button>
                    </div>
                    <div className="product-price" style={{ minWidth: 80, textAlign: 'right' }}>{money(i.lineTotal)}</div>
                  </div>
                ))}
                {toFree > 0 && <div className="notice" style={{ marginTop: 12 }}>Add {money(toFree)} more from {ship.sellerName} for free shipping.</div>}
              </div>
            );
          })}
        </div>

        <aside className="square-review-box static totals" aria-label="Order summary">
          <h2 style={{ margin: 0 }}>Order summary</h2>
          <div className="line"><span>Subtotal ({cart.itemCount} item{cart.itemCount === 1 ? '' : 's'})</span><span>{money(cart.subtotal)}</span></div>
          <div className="line"><span>Shipping</span><span>{cart.shipping === 0 ? 'FREE' : money(cart.shipping)}</span></div>
          {cart.shipments.length > 1 && <div className="notice">This will be placed as {cart.shipments.length} separate orders, one per seller.</div>}
          <div className="line grand"><span>Total</span><span>{money(cart.total)}</span></div>
          <button className="submit-btn block" onClick={() => navigate('/checkout')}>Proceed to checkout</button>
        </aside>
      </div>
    </div>
  );
}
