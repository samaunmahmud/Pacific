import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import type { CartItem } from '../api/types';
import { useCart } from '../cart/CartContext';
import { MoneyBig } from '../components/Price';
import { ProductImage } from '../components/ProductImage';
import { RecentlyViewed } from '../components/RecentlyViewed';
import { deliveryRange, money } from '../ui/format';
import { useToast } from '../ui/Toast';

const MAX_PER_ITEM = 10; // matches the server's per-item limit

/** Quantities the dropdown offers: up to the stock (and the per-item limit), always including what's in the cart. */
function quantityOptions(item: CartItem): number[] {
  const max = Math.max(Math.min(item.stock, MAX_PER_ITEM), 1);
  const options = Array.from({ length: max }, (_, n) => n + 1);
  return options.includes(item.quantity) ? options : [...options, item.quantity].sort((a, b) => a - b);
}

export function CartPage() {
  const { cart, setQuantity, remove, saveForLater } = useCart();
  const navigate = useNavigate();
  const toast = useToast();
  const [busyId, setBusyId] = useState<number | null>(null);

  async function run(id: number, fn: () => Promise<void>, done?: string) {
    setBusyId(id);
    try {
      await fn();
      if (done) toast.show(done);
    } catch (e) {
      toast.show(e instanceof Error ? e.message : 'Could not update your cart.', 'error');
    } finally {
      setBusyId(null);
    }
  }

  if (!cart) return <div className="loading">Loading…</div>;

  const savedList = cart.saved.length > 0 && (
    <section className="cart-box saved-box" aria-labelledby="saved-h">
      <h2 id="saved-h" className="saved-h">Saved for later ({cart.saved.length} item{cart.saved.length === 1 ? '' : 's'})</h2>
      <div className="saved-grid">
        {cart.saved.map((i) => {
          const busy = busyId === i.productId;
          return (
            <div key={i.productId} className={`saved-item ${busy ? 'busy' : ''}`}>
              <Link to={`/products/${i.productId}`} className="saved-thumb" aria-label={i.name}>
                <ProductImage imageUrl={i.imageUrl} categoryName={i.categoryName} alt={i.name} />
              </Link>
              <Link to={`/products/${i.productId}`} className="cart-item-title">{i.name}</Link>
              <b>{money(i.unitPrice)}</b>
              {i.stock === 0 ? <span className="bb-stock out">Currently unavailable</span> : <span className="bb-stock in">In stock</span>}
              <div className="cart-actions">
                <button className="cart-link-btn" disabled={busy || i.stock === 0} onClick={() => run(i.productId, () => saveForLater(i.productId, false), 'Moved to your cart')}>Move to cart</button>
                <button className="cart-link-btn" disabled={busy} onClick={() => run(i.productId, () => remove(i.productId))}>Delete</button>
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );

  if (cart.items.length === 0) {
    return (
      <div className="cart-page-wrap">
        <div className="cart-empty">
          <div className="cart-empty-art" aria-hidden="true">🛒</div>
          <div>
            <h1>Your Pacific cart is empty</h1>
            <p>Add things you'd like to buy and they'll wait for you here.</p>
            <div className="row-wrap">
              <Link className="cart-btn big link-btn" to="/products">Continue shopping</Link>
              <Link className="link-plain" to="/deals">See today's deals</Link>
            </div>
          </div>
        </div>
        {savedList}
        <RecentlyViewed />
      </div>
    );
  }

  const overStock = cart.items.some((i) => i.quantity > i.stock);
  const multiple = cart.shipments.length > 1;

  return (
    <div className="cart-page-wrap">
      <div className="cart-page">
        <section className="cart-main" aria-label="Items in your cart">
          <div className="cart-head">
            <h1>Shopping cart</h1>
            <span className="cart-price-h">Price</span>
          </div>

          {cart.shipments.map((ship) => {
            const lines = cart.items.filter((i) => i.sellerName === ship.sellerName);
            const units = lines.reduce((n, i) => n + i.quantity, 0);
            const toFree = ship.toFreeDelivery;
            const standard = ship.choices[0];
            return (
              <div key={ship.key} className="cart-box">
                <div className="cart-seller">
                  <span>Sold and shipped by{' '}
                    {ship.sellerSlug ? <Link to={`/sellers/${ship.sellerSlug}`}>{ship.sellerName}</Link> : <b>{ship.sellerName}</b>}
                  </span>
                  <span className="cart-delivery">
                    {ship.shipping === 0 ? <b className="free-delivery">FREE delivery</b> : <>Delivery {money(ship.shipping)}</>}
                    {standard && <> · <b>{deliveryRange(standard.from, standard.to)}</b></>}
                  </span>
                </div>

                {lines.map((i) => {
                  const busy = busyId === i.productId;
                  return (
                    <div key={i.productId} className={`cart-item ${busy ? 'busy' : ''}`}>
                      <Link to={`/products/${i.productId}`} className="cart-thumb" aria-label={i.name}>
                        <ProductImage imageUrl={i.imageUrl} categoryName={i.categoryName} alt={i.name} />
                      </Link>
                      <div className="cart-item-info">
                        <Link to={`/products/${i.productId}`} className="cart-item-title">{i.name}</Link>
                        {i.stock === 0 ? <div className="bb-stock out">Currently unavailable</div>
                          : i.quantity > i.stock ? <div className="bb-stock out">Only {i.stock} left in stock. Please lower the quantity.</div>
                          : i.stock <= 5 ? <div className="bb-stock low">Only {i.stock} left in stock</div>
                          : <div className="bb-stock in">In stock</div>}
                        <div className="cart-item-sub">
                          {money(i.unitPrice)} each
                          {i.listUnitPrice != null && <> <s className="was">{money(i.listUnitPrice)}</s> <span className="promo-label">{i.promotion}</span></>}
                        </div>
                        <div className="cart-actions">
                          <label className="bb-qty">
                            <span>Qty:</span>
                            <select value={i.quantity} disabled={busy} aria-label={`Quantity of ${i.name}`}
                              onChange={(e) => run(i.productId, () => setQuantity(i.productId, Number(e.target.value)))}>
                              {quantityOptions(i).map((n) => <option key={n} value={n}>{n}</option>)}
                            </select>
                          </label>
                          <button className="cart-link-btn" disabled={busy} onClick={() => run(i.productId, () => remove(i.productId))}>Delete</button>
                          <button className="cart-link-btn" disabled={busy} onClick={() => run(i.productId, () => saveForLater(i.productId, true), 'Saved for later')}>Save for later</button>
                        </div>
                      </div>
                      <div className="cart-item-price"><MoneyBig amount={i.lineTotal} /></div>
                    </div>
                  );
                })}

                <div className="cart-seller-sub">
                  Subtotal ({units} item{units === 1 ? '' : 's'}): <b>{money(ship.subtotal)}</b>
                </div>
                {toFree > 0 && <div className="cart-nudge">Add {money(toFree)} more from {ship.sellerName} for <b>FREE delivery</b>.</div>}
              </div>
            );
          })}
        </section>

        <aside className="cart-summary" aria-label="Order summary">
          {cart.shipping === 0 && <div className="cart-free">✓ Your order qualifies for <b>FREE delivery</b>.</div>}
          <div className="cart-sub-line">
            Subtotal ({cart.itemCount} item{cart.itemCount === 1 ? '' : 's'}): <MoneyBig amount={cart.subtotal} />
          </div>
          <div className="cart-sum-rows">
            <div><span>Delivery</span><span>{cart.shipping === 0 ? 'FREE' : money(cart.shipping)}</span></div>
            <div className="grand"><span>Order total</span><span>{money(cart.total)}</span></div>
          </div>
          {multiple && <div className="cart-note">Your items come from {cart.shipments.length} sellers, so this will be placed as {cart.shipments.length} separate orders.</div>}
          {overStock && <div className="cart-note warn" role="alert">Some items have less stock than you've chosen. Update the quantities to continue.</div>}
          <button className="cart-btn big" onClick={() => navigate('/checkout')} disabled={overStock}>Proceed to checkout</button>
          <Link to="/products" className="link-plain center">Continue shopping</Link>
        </aside>
      </div>
      {savedList}
      <RecentlyViewed />
    </div>
  );
}
