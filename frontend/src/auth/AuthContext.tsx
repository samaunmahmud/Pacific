import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, setUnauthorizedHandler, tokenStore } from '../api/client';
import type { AuthResponse, User } from '../api/types';

interface AuthState {
  user: User | null;
  /** True until we've checked whether a stored token is still valid. */
  loading: boolean;
  login: (identifier: string, password: string, portal: 'customer' | 'admin') => Promise<User>;
  register: (name: string, email: string, password: string) => Promise<User>;
  logout: () => void;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  const logout = useCallback(() => {
    tokenStore.set(null);
    setUser(null);
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(logout);
    return () => setUnauthorizedHandler(null);
  }, [logout]);

  // Restore the session from a stored token.
  useEffect(() => {
    if (!tokenStore.get()) {
      setLoading(false);
      return;
    }
    api<User>('/auth/me')
      .then(setUser)
      .catch(() => tokenStore.set(null))
      .finally(() => setLoading(false));
  }, []);

  const finish = useCallback((res: AuthResponse) => {
    tokenStore.set(res.token);
    setUser(res.user);
    return res.user;
  }, []);

  const login = useCallback(
    async (identifier: string, password: string, portal: 'customer' | 'admin') =>
      finish(
        await api<AuthResponse>(portal === 'admin' ? '/auth/admin/login' : '/auth/login', {
          method: 'POST',
          body: { identifier, password },
        }),
      ),
    [finish],
  );

  const register = useCallback(
    async (name: string, email: string, password: string) =>
      finish(await api<AuthResponse>('/auth/register', { method: 'POST', body: { name, email, password } })),
    [finish],
  );

  const value = useMemo(() => ({ user, loading, login, register, logout }), [user, loading, login, register, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}
