import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api } from '../api/client';
import type { Cart } from '../api/types';
import { useAuth } from '../auth/AuthContext';

interface CartState {
  cart: Cart | null;
  count: number;
  add: (productId: number, quantity?: number) => Promise<void>;
  setQuantity: (productId: number, quantity: number) => Promise<void>;
  remove: (productId: number) => Promise<void>;
  refresh: () => Promise<void>;
}

const CartContext = createContext<CartState | null>(null);

/** The cart lives on the server (per customer); this keeps a client copy for the header badge and cart page. */
export function CartProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const [cart, setCart] = useState<Cart | null>(null);
  const isCustomer = user?.role === 'CUSTOMER';

  const refresh = useCallback(async () => {
    if (!isCustomer) {
      setCart(null);
      return;
    }
    try {
      setCart(await api<Cart>('/cart'));
    } catch {
      /* the badge simply stays stale; the cart page shows the real error */
    }
  }, [isCustomer]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const add = useCallback(async (productId: number, quantity = 1) => {
    setCart(await api<Cart>('/cart/items', { method: 'POST', body: { productId, quantity } }));
  }, []);

  const setQuantity = useCallback(async (productId: number, quantity: number) => {
    setCart(await api<Cart>(`/cart/items/${productId}`, { method: 'PATCH', body: { quantity } }));
  }, []);

  const remove = useCallback(async (productId: number) => {
    setCart(await api<Cart>(`/cart/items/${productId}`, { method: 'DELETE' }));
  }, []);

  const value = useMemo(
    () => ({ cart, count: cart?.itemCount ?? 0, add, setQuantity, remove, refresh }),
    [cart, add, setQuantity, remove, refresh],
  );
  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export function useCart(): CartState {
  const ctx = useContext(CartContext);
  if (!ctx) throw new Error('useCart must be used inside <CartProvider>');
  return ctx;
}
