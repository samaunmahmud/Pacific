import { useEffect, useState, type FormEvent } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import avatar from '../assets/avatarCustomerLogin.png';
import cartIcon from '../assets/cart1.png';
import { api } from '../api/client';
import type { Category } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { useCart } from '../cart/CartContext';
import { useSeller } from '../seller/SellerContext';
import { useAsync } from '../ui/useAsync';

function Header() {
  const { user, logout } = useAuth();
  const { count } = useCart();
  const navigate = useNavigate();
  const location = useLocation();
  const [params] = useSearchParams();
  const [q, setQ] = useState(params.get('q') ?? '');
  const isAdmin = user?.role === 'ADMIN';

  // keep the box in sync when the URL changes (e.g. clearing the search)
  useEffect(() => {
    if (location.pathname === '/products') setQ(params.get('q') ?? '');
  }, [location.pathname, params]);

  function submit(e: FormEvent) {
    e.preventDefault();
    navigate(isAdmin ? `/admin/products?q=${encodeURIComponent(q)}` : `/products?q=${encodeURIComponent(q)}`);
  }

  function signOut() {
    logout();
    navigate('/');
  }

  return (
    <header className="header-box">
      <div className="logo-block">
        <Link to={isAdmin ? '/admin' : '/'} className="logo-text">Pacific ★</Link>
        <span className="location"><span aria-hidden="true">📍 </span>Uxbridge, England</span>
      </div>

      <form className="search-container" role="search" onSubmit={submit}>
        <span className="search-icon" aria-hidden="true">🔍</span>
        <input className="transparent-search" value={q} onChange={(e) => setQ(e.target.value)} placeholder={isAdmin ? 'Search products…' : 'Search Pacific...'} aria-label="Search" maxLength={100} />
        <button className="search-submit-btn" type="submit">Search</button>
      </form>

      <div className="header-actions">
        {isAdmin ? (
          <div className="action-item" style={{ cursor: 'default' }}>
            <span className="action-small">Signed in as admin</span>
            <span className="action-label">{user?.name}</span>
          </div>
        ) : (
          <>
            <Link to={user ? '/account/reviews' : '/login'} className="action-item">
              <span className="action-small">Hello, {user ? user.name.split(' ')[0] : 'sign in'}</span>
              <span className="action-label">Account and Lists</span>
            </Link>
            <Link to="/orders" className="action-item">
              <span className="action-small">Returns</span>
              <span className="action-label">and Orders</span>
            </Link>
            <Link to="/cart" className="action-item action-center" aria-label={`Cart, ${count} items`}>
              <img className="cart-icon" src={cartIcon} alt="" />
              {count > 0 && <span className="cart-badge">{count}</span>}
              <span className="action-label">Cart</span>
            </Link>
          </>
        )}
        {user ? (
          <button className="action-item action-center logout-item" onClick={signOut}>
            <span className="avatar-ring"><img src={avatar} alt="" /></span>
            <span className="action-label">Logout</span>
          </button>
        ) : (
          <Link to="/login" className="action-item action-center logout-item">
            <span className="avatar-ring"><img src={avatar} alt="" /></span>
            <span className="action-label">Sign in</span>
          </Link>
        )}
      </div>
    </header>
  );
}

function NavBar() {
  const { user } = useAuth();
  const { seller } = useSeller();
  const location = useLocation();
  const categories = useAsync(() => api<Category[]>('/categories'), []);

  if (user?.role === 'CUSTOMER' && seller?.status === 'APPROVED' && location.pathname.startsWith('/seller')) {
    return (
      <nav className="nav-bar" aria-label="Seller Central">
        <NavLink to="/seller" end className="nav-link-bold">☰ Seller Central</NavLink>
        <NavLink to="/seller/products" className="nav-link">Products</NavLink>
        <NavLink to="/seller/orders" className="nav-link">Orders</NavLink>
        <NavLink to="/seller/earnings" className="nav-link">Earnings</NavLink>
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
        <NavLink to="/" end className="nav-link">View Storefront</NavLink>
      </nav>
    );
  }
  return (
    <nav className="nav-bar" aria-label="Shop">
      <NavLink to="/products" end className="nav-link-bold">☰ All</NavLink>
      {(categories.data ?? []).map((c) => (
        <Link key={c.id} to={`/products?category=${c.slug}`} className="nav-link">{c.name}</Link>
      ))}
      <NavLink to="/deals" className="nav-link">Today's Deals</NavLink>
      <NavLink to="/wishlist" className="nav-link">Wish List</NavLink>
      <NavLink to="/account/reviews" className="nav-link">My Reviews</NavLink>
      {seller?.status === 'APPROVED'
        ? <NavLink to="/seller" className="nav-link-bold">Seller Central</NavLink>
        : <NavLink to="/sell" className="nav-link-bold">Sell on Pacific</NavLink>}
    </nav>
  );
}

export function Layout() {
  const location = useLocation();
  // start each page at the top
  useEffect(() => {
    window.scrollTo(0, 0);
  }, [location.pathname]);
  return (
    <div className="app-shell">
      <Header />
      <NavBar />
      <main className="app-main"><Outlet /></main>
      <footer className="footer">© Pacific Marketplace · Payments are taken on delivery.</footer>
    </div>
  );
}
