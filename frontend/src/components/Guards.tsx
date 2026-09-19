import { Navigate, Outlet, useLocation } from 'react-router-dom';
import type { Role } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { useSeller } from '../seller/SellerContext';

/** Sends visitors to the matching login page if they aren't signed in with the required role. */
export function RequireRole({ role }: { role: Role }) {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) return <div className="loading">Loading…</div>;
  if (!user || user.role !== role) {
    return <Navigate to={role === 'ADMIN' ? '/admin/login' : '/login'} replace state={{ next: location.pathname + location.search }} />;
  }
  return <Outlet />;
}

/** Seller Central: signed-in customers with an approved seller profile. Anyone else is pointed at /sell. */
export function RequireSeller() {
  const { user, loading } = useAuth();
  const { seller, loading: sellerLoading } = useSeller();
  const location = useLocation();
  if (loading) return <div className="loading">Loading…</div>;
  if (!user || user.role !== 'CUSTOMER') return <Navigate to="/login" replace state={{ next: location.pathname + location.search }} />;
  if (sellerLoading) return <div className="loading">Loading…</div>;
  if (!seller || seller.status !== 'APPROVED') return <Navigate to="/sell" replace />;
  return <Outlet />;
}
