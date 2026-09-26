import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { api } from '../api/client';
import type { Offer, Product, ProductReviews, Review } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { useCart } from '../cart/CartContext';
import { OtherSellers, SellThisToo } from '../components/Offers';
import { Price } from '../components/Price';
import { ProductGallery } from '../components/ProductGallery';
import { QandA } from '../components/QandA';
import { RecentlyViewed } from '../components/RecentlyViewed';
import { CustomerReviewCard } from '../components/ReviewCard';
import { Stars } from '../components/Stars';
import { WishlistButton } from '../components/WishlistButton';
import { useSeller } from '../seller/SellerContext';
import { deliveryRange, money, timeLeft } from '../ui/format';
import { recordView } from '../ui/recent';
import { useToast } from '../ui/Toast';
import { useAsync } from '../ui/useAsync';

/** What the buy box sells: the winning offer, or the page's own listing while offers load. */
type BuyTarget = Pick<Offer, 'productId' | 'sellerName' | 'sellerSlug' | 'price' | 'listPrice' | 'discountPercent' | 'stock' | 'condition' | 'conditionLabel'>;

const SORTS: [string, string][] = [
  ['newest', 'Newest'],
  ['helpful', 'Most Helpful'],
  ['highest', 'Highest Rating'],
  ['lowest', 'Lowest Rating'],
];

export function ProductDetail() {
  const { id } = useParams();
  const { user } = useAuth();
  const { add, cart } = useCart();
  const { seller: mySeller } = useSeller();
  const toast = useToast();
  const navigate = useNavigate();
  const location = useLocation();
  const [qty, setQty] = useState(1);
  const [busy, setBusy] = useState(false);
  const [sort, setSort] = useState('newest');

  const product = useAsync(() => api<Product>(`/products/${id}`), [id]);
  const offers = useAsync(() => api<Offer[]>(`/products/${id}/offers`).catch((): Offer[] => []), [id]);
  const [busyOffer, setBusyOffer] = useState<number | null>(null);
  const reviews = useAsync(() => api<ProductReviews>(`/products/${id}/reviews`, { query: { sort } }), [id, sort, user?.id]);
  const loadedId = product.data?.id;
  useEffect(() => {
    if (loadedId !== undefined) recordView(loadedId);
  }, [loadedId]);

  if (product.error && !product.data) {
    return (
      <div className="page page-narrow">
        <div className="notice error">{product.error}</div>
        <Link className="submit-btn" style={{ alignSelf: 'flex-start' }} to="/products">Back to all products</Link>
      </div>
    );
  }
  const p = product.data;
  if (!p) return <div className="loading">Loading…</div>;

  // The buy box: the best offer on sale (another seller's, perhaps). Until offers load, the page's own listing.
  const ownOffer: BuyTarget = { productId: p.id, sellerName: p.sellerName, sellerSlug: p.sellerSlug, price: p.price,
    listPrice: p.listPrice, discountPercent: p.discountPercent, stock: p.stock, condition: p.condition, conditionLabel: 'New' };
  const best = offers.data?.[0];
  const box: BuyTarget = best ?? ownOffer;
  const cutoff = timeLeft(best?.orderWithin ?? null);
  const others = offers.data?.slice(1) ?? [];
  const quantityInCart = (productId: number) => cart?.items.find((i) => i.productId === productId)?.quantity ?? 0;
  const inCart = quantityInCart(box.productId);
  const canAddMore = Math.max(0, Math.min(box.stock, 10) - inCart);
  const alreadySelling = !!mySeller && (p.sellerSlug === mySeller.slug || (offers.data ?? []).some((o) => o.sellerSlug === mySeller.slug));
  const canSellToo = user?.role === 'CUSTOMER' && mySeller?.status === 'APPROVED' && !alreadySelling && offers.data !== undefined;

  function signedInCustomer() {
    if (!user) { navigate('/login', { state: { next: location.pathname } }); return false; }
    if (user.role !== 'CUSTOMER') { toast.show('Sign in with a customer account to shop.', 'error'); return false; }
    return true;
  }

  async function addToCart() {
    if (!p || !signedInCustomer()) return;
    setBusy(true);
    try {
      await add(box.productId, qty);
      toast.show(`Added ${qty} × ${p.name} to your cart`);
      setQty(1);
    } catch (e) {
      toast.show(e instanceof Error ? e.message : 'Could not add to cart.', 'error');
    } finally {
      setBusy(false);
    }
  }

  async function addOffer(o: Offer) {
    if (!p || !signedInCustomer()) return;
    setBusyOffer(o.productId);
    try {
      await add(o.productId, 1);
      toast.show(`Added ${p.name} from ${o.sellerName} to your cart`);
    } catch (e) {
      toast.show(e instanceof Error ? e.message : 'Could not add to cart.', 'error');
    } finally {
      setBusyOffer(null);
    }
  }

  async function buyNow() {
    if (!p || !signedInCustomer()) return;
    setBusy(true);
    try {
      await add(box.productId, qty);
      navigate('/checkout');
    } catch (e) {
      toast.show(e instanceof Error ? e.message : 'Could not start checkout.', 'error');
      setBusy(false);
    }
  }

  const data = reviews.data;
  const replace = (r: Review) => reviews.setData((prev) => prev && { ...prev, reviews: prev.reviews.map((x) => (x.id === r.id ? r : x)) });
  const drop = () => reviews.reload(); // counts/eligibility change too, so refetch

  return (
    <div className="page">
      <nav className="crumbs" aria-label="Breadcrumb">
        <Link to="/products">All products</Link>
        {p.category && <> <span aria-hidden="true">›</span> <Link to={`/products?category=${p.category.slug}`}>{p.category.name}</Link></>}
      </nav>

      <div className="pdp">
        <ProductGallery key={p.id} product={p} />

        <div className="pdp-info">
          <h1>{p.name}</h1>
          <div className="pdp-sub">
            Sold by {p.sellerSlug ? <Link to={`/sellers/${p.sellerSlug}`}>{p.sellerName}</Link> : <b>{p.sellerName}</b>}
            {p.sellerSlug && user?.role !== 'ADMIN' && mySeller?.slug !== p.sellerSlug && (
              <> · <Link to={`/messages/new?seller=${p.sellerSlug}&product=${p.id}`} className="message-seller">Message the seller</Link></>
            )}
          </div>
          <div className="pdp-rating"><Stars value={p.ratingAvg} count={p.ratingCount} />{p.ratingCount > 0 && <a href="#reviews-h" className="see-reviews">See reviews</a>}</div>
          <hr />
          <Price price={box.price} listPrice={box.listPrice} discountPercent={box.discountPercent} large />
          <div className="muted" style={{ fontSize: 12 }}>Prices include VAT where applicable.</div>
          {others.length > 0 && <a href="#other-sellers-h" className="see-reviews">{others.length} other seller{others.length === 1 ? '' : 's'} from {money(Math.min(...others.map((o) => o.price)))}</a>}
          {p.description && (
            <>
              <h2 className="about-h">About this item</h2>
              <ul className="about-list">
                {p.description.split('\n').filter((line) => line.trim()).map((line) => <li key={line}>{line}</li>)}
              </ul>
            </>
          )}
        </div>

        <aside className="buy-box" aria-label="Buy box">
          <Price price={box.price} listPrice={null} discountPercent={0} large />
          {box.condition !== 'NEW' && <div className="bb-condition">Condition: <b>{box.conditionLabel}</b></div>}
          {best ? (
            <div className="bb-delivery">
              {best.standardFee === 0
                ? <><b className="free-delivery">FREE delivery</b> <b>{deliveryRange(best.standardFrom, best.standardTo)}</b>.</>
                : <>{money(best.standardFee)} delivery <b>{deliveryRange(best.standardFrom, best.standardTo)}</b>. FREE on {best.sellerName} orders over {money(best.freeDeliveryFrom)}.</>}
              <div>Or fastest delivery <b>{deliveryRange(best.expressDate, null)}</b> ({money(best.expressFee)}).{cutoff && <> Order within <span className="bb-cutoff">{cutoff}</span>.</>}</div>
            </div>
          ) : <div className="bb-delivery">Delivery costs are shown at checkout.</div>}
          {box.stock === 0 ? <div className="bb-stock out">Currently unavailable.</div>
            : box.stock <= 5 ? <div className="bb-stock low">Only {box.stock} left in stock.</div>
            : <div className="bb-stock in">In stock</div>}
          {box.stock > 0 && (
            <>
              <label className="bb-qty">
                <span>Quantity:</span>
                <select value={Math.min(qty, Math.max(canAddMore, 1))} onChange={(e) => setQty(Number(e.target.value))} disabled={canAddMore === 0} aria-label="Quantity">
                  {Array.from({ length: Math.max(canAddMore, 1) }, (_, n) => n + 1).map((n) => <option key={n} value={n}>{n}</option>)}
                </select>
              </label>
              <button className="cart-btn big" onClick={addToCart} disabled={busy || canAddMore === 0}>
                {canAddMore === 0 ? 'Max quantity in cart' : busy ? 'Adding…' : 'Add to cart'}
              </button>
              <button className="buy-btn" onClick={buyNow} disabled={busy || canAddMore === 0}>Buy now</button>
              {inCart > 0 && <Link to="/cart" className="in-cart-link">{inCart} in your cart. View cart</Link>}
            </>
          )}
          <dl className="bb-facts">
            <dt>Payment</dt><dd>Secure transaction: card or pay on delivery</dd>
            <dt>Ships from</dt><dd>{box.sellerName}</dd>
            <dt>Sold by</dt><dd>{box.sellerSlug ? <Link to={`/sellers/${box.sellerSlug}`}>{box.sellerName}</Link> : box.sellerName}</dd>
          </dl>
          <WishlistButton productId={p.id} label />
          {canSellToo && <SellThisToo productId={p.id} onDone={() => { offers.reload(); toast.show('Your offer is live on this page'); }} />}
        </aside>
      </div>

      <OtherSellers offers={others} onAdd={addOffer} busyId={busyOffer} inCart={quantityInCart} />

      <hr />

      <section className="stack" style={{ gap: 20 }} aria-labelledby="reviews-h">
        <div className="row-wrap">
          <h2 className="section-title" id="reviews-h">Customer reviews</h2>
          <span className="spacer" />
          <select className="pill-select" value={sort} onChange={(e) => setSort(e.target.value)} aria-label="Sort reviews">
            {SORTS.map(([v, label]) => <option key={v} value={v}>{label}</option>)}
          </select>
        </div>

        {reviews.error && <div className="notice error">{reviews.error}</div>}
        {data && (
          <>
            <div className="square-review-box static summary-grid">
              <div className="stack" style={{ gap: 8 }}>
                <div className="big-rating">{data.summary.count ? data.summary.average.toFixed(1) : '–'}</div>
                <Stars value={data.summary.average} />
                <div className="muted">{data.summary.count} review{data.summary.count === 1 ? '' : 's'}</div>
              </div>
              <div className="stack" style={{ gap: 6 }}>
                {[5, 4, 3, 2, 1].map((star) => {
                  const n = data.summary.distribution[star - 1];
                  return (
                    <div key={star} className="dist-row">
                      <span style={{ width: 28 }}>{star} ★</span>
                      <div className="progress"><span style={{ width: `${data.summary.count ? (n / data.summary.count) * 100 : 0}%` }} /></div>
                      <span style={{ width: 24, textAlign: 'right' }}>{n}</span>
                    </div>
                  );
                })}
              </div>
            </div>

            <div>
              {!data.eligibility.signedIn ? (
                <Link className="submit-btn" to="/login" state={{ next: location.pathname }}>Sign in to write a review</Link>
              ) : data.eligibility.hasReviewed ? (
                <div className="notice">You've reviewed this product — thanks! You can find it under <Link to="/account/reviews">My Reviews</Link>.</div>
              ) : data.eligibility.purchased ? (
                <Link className="submit-btn" to={`/products/${p.id}/review`}>Write a review</Link>
              ) : (
                <div className="notice">You can review this product once you've bought it.</div>
              )}
            </div>

            {data.reviews.length === 0 ? (
              <div className="empty">No reviews yet.</div>
            ) : (
              <div className="review-list">
                {data.reviews.map((r) => <CustomerReviewCard key={r.id} review={r} onChange={replace} onDelete={drop} />)}
              </div>
            )}
          </>
        )}
      </section>

      <hr />
      <QandA productId={p.id} />
      <RecentlyViewed exclude={p.id} />
    </div>
  );
}
