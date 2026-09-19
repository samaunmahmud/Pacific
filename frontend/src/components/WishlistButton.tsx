import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { useWishlist } from '../cart/WishlistContext';
import { useToast } from '../ui/Toast';

/** Heart toggle. Signed-out visitors are sent to sign in first. */
export function WishlistButton({ productId, label }: { productId: number; label?: boolean }) {
  const { user } = useAuth();
  const { has, toggle } = useWishlist();
  const toast = useToast();
  const navigate = useNavigate();
  const location = useLocation();
  const saved = has(productId);

  async function click(e: React.MouseEvent) {
    e.preventDefault();
    e.stopPropagation();
    if (!user) return navigate('/login', { state: { next: location.pathname + location.search } });
    if (user.role !== 'CUSTOMER') return toast.show('Sign in with a customer account to save items.', 'error');
    try {
      const now = await toggle(productId);
      toast.show(now ? 'Saved to your wish list' : 'Removed from your wish list');
    } catch (err) {
      toast.show(err instanceof Error ? err.message : 'Could not update your wish list.', 'error');
    }
  }

  return (
    <button className={`heart-btn ${saved ? 'on' : ''} ${label ? 'with-label' : ''}`} onClick={click} aria-pressed={saved} aria-label={saved ? 'Remove from wish list' : 'Add to wish list'} title={saved ? 'Remove from wish list' : 'Add to wish list'}>
      <span aria-hidden="true">{saved ? '♥' : '♡'}</span>
      {label && <span>{saved ? 'Saved' : 'Save for later'}</span>}
    </button>
  );
}
