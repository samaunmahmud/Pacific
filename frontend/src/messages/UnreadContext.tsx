import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useLocation } from 'react-router-dom';
import { api } from '../api/client';
import type { UnreadMessages } from '../api/types';
import { useAuth } from '../auth/AuthContext';

const NONE: UnreadMessages = { asBuyer: 0, asSeller: 0 };

const UnreadContext = createContext<{ unread: UnreadMessages; refresh: () => void }>({ unread: NONE, refresh: () => {} });

/** Conversations with unread messages, for the header and Seller Central badges. Checked on every page change. */
export function UnreadProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const { pathname } = useLocation();
  const [unread, setUnread] = useState<UnreadMessages>(NONE);
  const customerId = user?.role === 'CUSTOMER' ? user.id : null;

  const refresh = useCallback(() => {
    if (customerId === null) return setUnread(NONE);
    api<UnreadMessages>('/messages/unread').then(setUnread).catch(() => {});
  }, [customerId]);

  useEffect(refresh, [refresh, pathname]);

  const value = useMemo(() => ({ unread: customerId === null ? NONE : unread, refresh }), [customerId, unread, refresh]);
  return <UnreadContext.Provider value={value}>{children}</UnreadContext.Provider>;
}

export const useUnread = () => useContext(UnreadContext);
