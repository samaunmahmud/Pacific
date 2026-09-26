import { useEffect, useState, type FormEvent } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import cartIcon from '../assets/cart1.png';
import { useAuth } from '../auth/AuthContext';
import { useCart } from '../cart/CartContext';
import { useSeller } from '../seller/SellerContext';
import { UnreadProvider, useUnread } from '../messages/UnreadContext';
import { useCategories } from '../ui/useCategories';
import { ConfirmEmailNotice } from './ConfirmEmailNotice';
import { useSearchSuggest } from './SearchSuggest';

function Header() {
  const { user, logout } = useAuth();
  const { count } = useCart();
  const { unread } = useUnread();
  const navigate = useNavigate();
  const location = useLocation();
  const [params] = useSearchParams();
  const categories = useCategories();
  const [q, setQ] = useState(params.get('q') ?? '');
  const [cat, setCat] = useState(params.get('category') ?? '');
  const isAdmin = user?.role === 'ADMIN';
  const suggest = useSearchSuggest(isAdmin ? '' : q);

  // keep the box in sync when the URL changes (e.g. clearing the search)
  useEffect(() => {
    if (location.pathname === '/products') {
      setQ(params.get('q') ?? '');
      setCat(params.get('category') ?? '');
    }
  }, [location.pathname, params]);

  function submit(e: FormEvent) {
    e.preventDefault();
    suggest.close();
    const query = new URLSearchParams();
    if (q.trim()) query.set('q', q.trim());
    if (!isAdmin && cat) query.set('category', cat);
    navigate(`${isAdmin ? '/admin/products' : '/products'}${query.size ? `?${query}` : ''}`);
  }

  function signOut() {
    logout();
    navigate('/');
  }

  return (
    <header className="header-box">
      <Link to={isAdmin ? '/admin' : '/'} className="logo-text" aria-label="Pacific home">Pacific<span className="logo-star" aria-hidden="true">★</span></Link>

      {!isAdmin && (
        <span className="deliver-to" aria-label="Delivering to Uxbridge">
          <span className="action-small">Deliver to</span>
          <span className="action-label"><span aria-hidden="true">📍 </span>Uxbridge</span>
        </span>
      )}

      <form className="search-container" role="search" onSubmit={submit}>
        {!isAdmin && (
          <select className="search-cat" value={cat} onChange={(e) => setCat(e.target.value)} aria-label="Search in category">
            <option value="">All</option>
            {categories.map((c) => <option key={c.id} value={c.slug}>{c.name}</option>)}
          </select>
        )}
        <input className="transparent-search" value={q} onChange={(e) => { setQ(e.target.value); suggest.open(); }}
          onFocus={suggest.open} onBlur={suggest.close} onKeyDown={(e) => { suggest.onKey(e); }}
          placeholder={isAdmin ? 'Search products…' : 'Search Pacific'} aria-label="Search" maxLength={100} autoComplete="off"
          role="combobox" aria-expanded={!!suggest.list} aria-autocomplete="list" aria-activedescendant={suggest.activeId} />
        {suggest.list}
        <button className="search-submit-btn" type="submit" aria-label="Search"><span aria-hidden="true">🔍</span></button>
      </form>

      <div className="header-actions">
        {isAdmin ? (
          <>
            <div className="action-item" style={{ cursor: 'default' }}>
              <span className="action-small">Signed in as admin</span>
              <span className="action-label">{user?.name}</span>
            </div>
            <button className="action-item" onClick={signOut}>
              <span className="action-small">Not you?</span>
              <span className="action-label">Sign out</span>
            </button>
          </>
        ) : (
          <>
            {user ? (
              <>
                <Link to="/account" className="action-item" title="Your account">
                  <span className="action-small">Hello, {user.name.split(' ')[0]}</span>
                  <span className="action-label">Your account</span>
                </Link>
                <Link to="/messages" className="action-item" aria-label={unread.asBuyer ? `Messages, ${unread.asBuyer} unread` : 'Messages'}>
                  <span className="action-small">Your</span>
                  <span className="action-label">Messages{unread.asBuyer > 0 && <span className="count-badge">{unread.asBuyer}</span>}</span>
                </Link>
                <button className="action-item" onClick={signOut} title="Sign out">
                  <span className="action-small">Not you?</span>
                  <span className="action-label">Sign out</span>
                </button>
              </>
            ) : (
              <Link to="/login" className="action-item">
                <span className="action-small">Hello, sign in</span>
                <span className="action-label">Account and Lists</span>
              </Link>
            )}
            <Link to="/orders" className="action-item">
              <span className="action-small">Returns</span>
              <span className="action-label">and Orders</span>
            </Link>
            <Link to="/cart" className="action-item cart-link" aria-label={`Cart, ${count} items`}>
              <span className="cart-wrap">
                <img className="cart-icon" src={cartIcon} alt="" />
                <span className="cart-badge">{count}</span>
              </span>
              <span className="action-label">Cart</span>
            </Link>
          </>
        )}
      </div>
    </header>
  );
}

function NavBar() {
  const { user } = useAuth();
  const { unread } = useUnread();
  const { seller } = useSeller();
  const location = useLocation();
  const categories = useCategories();

  if (user?.role === 'CUSTOMER' && seller?.status === 'APPROVED' && location.pathname.startsWith('/seller')) {
    return (
      <nav className="nav-bar" aria-label="Seller Central">
        <NavLink to="/seller" end className="nav-link-bold">☰ Seller Central</NavLink>
        <NavLink to="/seller/products" className="nav-link">Products</NavLink>
        <NavLink to="/seller/orders" className="nav-link">Orders</NavLink>
        <NavLink to="/seller/returns" className="nav-link">Returns</NavLink>
        <NavLink to="/seller/earnings" className="nav-link">Earnings</NavLink>
        <NavLink to="/seller/messages" className="nav-link">Messages{unread.asSeller > 0 && <span className="count-badge" aria-label={`${unread.asSeller} unread`}>{unread.asSeller}</span>}</NavLink>
        <NavLink to="/seller/promotions" className="nav-link">Promotions</NavLink>
        <NavLink to="/seller/questions" className="nav-link">Questions</NavLink>
        <NavLink to="/seller/settings" className="nav-link">Store settings</NavLink>
        <Link to="/" className="nav-link">← Back to shopping</Link>
      </nav>
    );
  }

  if (user?.role === 'ADMIN') {
    return (
      <nav className="nav-bar" aria-label="Admin">
        <NavLink to="/admin" end className="nav-link-bold">☰ Dashboard</NavLink>
        <NavLink to="/admin/products" className="nav-link">Products</NavLink>
        <NavLink to="/admin/orders" className="nav-link">Orders</NavLink>
        <NavLink to="/admin/sellers" className="nav-link">Sellers</NavLink>
        <NavLink to="/admin/flagged" className="nav-link">Flagged Reviews</NavLink>
        <NavLink to="/admin/returns" className="nav-link">Returns</NavLink>
        <NavLink to="/admin/promotions" className="nav-link">Promotions</NavLink>
        <NavLink to="/admin/emails" className="nav-link">Emails</NavLink>
        <NavLink to="/" end className="nav-link">View Storefront</NavLink>
      </nav>
    );
  }
  return (
    <nav className="nav-bar" aria-label="Shop">
      <NavLink to="/products" end className="nav-link-bold">☰ All</NavLink>
      {categories.map((c) => (
        <Link key={c.id} to={`/products?category=${c.slug}`} className="nav-link">{c.name}</Link>
      ))}
      <NavLink to="/deals" className="nav-link deals-link">Today's Deals</NavLink>
      <NavLink to="/wishlist" className="nav-link">Wish List</NavLink>
      {seller?.status === 'APPROVED'
        ? <NavLink to="/seller" className="nav-link-bold">Seller Central</NavLink>
        : <NavLink to="/sell" className="nav-link-bold">Sell on Pacific</NavLink>}
    </nav>
  );
}

function Footer() {
  const { user } = useAuth();
  const { seller } = useSeller();
  const categories = useCategories();
  return (
    <footer className="site-footer">
      <button className="back-to-top" onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })}>Back to top</button>
      <div className="footer-cols">
        <div>
          <h4>Shop</h4>
          <Link to="/products">All products</Link>
          <Link to="/deals">Today's Deals</Link>
          {categories.slice(0, 4).map((c) => <Link key={c.id} to={`/products?category=${c.slug}`}>{c.name}</Link>)}
        </div>
        <div>
          <h4>Your account</h4>
          <Link to={user ? '/account' : '/login'}>Your account</Link>
          <Link to={user ? '/orders' : '/login'}>Your orders</Link>
          <Link to="/wishlist">Wish list</Link>
          <Link to="/account/reviews">Your reviews</Link>
          <Link to="/cart">Cart</Link>
        </div>
        <div>
          <h4>Sell with us</h4>
          {seller?.status === 'APPROVED' ? <Link to="/seller">Seller Central</Link> : <Link to="/sell">Sell on Pacific</Link>}
          <span>Reach shoppers with your own storefront</span>
          <span>Simple commission, no listing fees</span>
        </div>
        <div>
          <h4>Payments</h4>
          <span>Pay securely by card</span>
          <span>or pay on delivery</span>
          <span>Card details never touch our servers</span>
        </div>
      </div>
      <div className="footer-bottom">
        <span className="footer-logo">Pacific<span aria-hidden="true">★</span></span>
        <span>© Pacific Marketplace</span>
      </div>
    </footer>
  );
}

/** Pages that explain what confirming the email unlocks themselves, so the site-wide reminder would repeat it. */
const OWN_CONFIRM_NOTICE = ['/checkout', '/sell'];

export function Layout() {
  const location = useLocation();
  // start each page at the top
  useEffect(() => {
    window.scrollTo(0, 0);
  }, [location.pathname]);
  return (
    <UnreadProvider>
      <div className="app-shell">
        <Header />
        <NavBar />
        <main className="app-main">
          {!OWN_CONFIRM_NOTICE.includes(location.pathname) && <div className="confirm-email-bar"><ConfirmEmailNotice /></div>}
          <Outlet />
        </main>
        <Footer />
      </div>
    </UnreadProvider>
  );
}
