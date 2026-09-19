import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api } from '../api/client';
import type { Seller } from '../api/types';
import { useAuth } from '../auth/AuthContext';

interface SellerState {
  /** The signed-in customer's seller profile, or null if they haven't applied (or aren't a customer). */
  seller: Seller | null;
  /** True from the moment a customer is known until their profile has been fetched (so guards don't redirect early). */
  loading: boolean;
  refresh: () => Promise<void>;
}

const SellerContext = createContext<SellerState | null>(null);

/** Sellers are ordinary customer accounts with a seller profile; this tracks that profile's status. */
export function SellerProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  // `userId` records whose profile this is, so "not fetched yet" is distinguishable from "not a seller".
  const [state, setState] = useState<{ userId: number | null; seller: Seller | null }>({ userId: null, seller: null });
  const customerId = user?.role === 'CUSTOMER' ? user.id : null;

  const refresh = useCallback(async () => {
    if (customerId === null) {
      setState({ userId: null, seller: null });
      return;
    }
    let seller: Seller | null = null;
    try {
      seller = (await api<Seller | undefined>('/seller/me')) ?? null; // 204 = hasn't applied
    } catch (e) {
      console.error(e);
    }
    setState({ userId: customerId, seller });
  }, [customerId]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const loading = customerId !== null && state.userId !== customerId;
  const value = useMemo(() => ({ seller: loading ? null : state.seller, loading, refresh }), [state.seller, loading, refresh]);
  return <SellerContext.Provider value={value}>{children}</SellerContext.Provider>;
}

export function useSeller(): SellerState {
  const ctx = useContext(SellerContext);
  if (!ctx) throw new Error('useSeller must be used inside <SellerProvider>');
  return ctx;
}
