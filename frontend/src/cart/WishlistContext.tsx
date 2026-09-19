import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api } from '../api/client';
import { useAuth } from '../auth/AuthContext';

interface WishlistState {
  ids: Set<number>;
  has: (productId: number) => boolean;
  toggle: (productId: number) => Promise<boolean>;
}

const WishlistContext = createContext<WishlistState | null>(null);

export function WishlistProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const [ids, setIds] = useState<Set<number>>(new Set());
  const isCustomer = user?.role === 'CUSTOMER';

  useEffect(() => {
    if (!isCustomer) {
      setIds(new Set());
      return;
    }
    api<number[]>('/wishlist/ids')
      .then((list) => setIds(new Set(list)))
      .catch(() => {});
  }, [isCustomer, user?.id]);

  const has = useCallback((id: number) => ids.has(id), [ids]);

  /** Returns true if the product is now saved. */
  const toggle = useCallback(
    async (productId: number) => {
      const saved = ids.has(productId);
      await api(`/wishlist/${productId}`, { method: saved ? 'DELETE' : 'PUT' });
      setIds((prev) => {
        const next = new Set(prev);
        if (saved) next.delete(productId);
        else next.add(productId);
        return next;
      });
      return !saved;
    },
    [ids],
  );

  const value = useMemo(() => ({ ids, has, toggle }), [ids, has, toggle]);
  return <WishlistContext.Provider value={value}>{children}</WishlistContext.Provider>;
}

export function useWishlist(): WishlistState {
  const ctx = useContext(WishlistContext);
  if (!ctx) throw new Error('useWishlist must be used inside <WishlistProvider>');
  return ctx;
}
