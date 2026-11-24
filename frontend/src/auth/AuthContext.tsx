import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { api, getToken, setToken, setUnauthorizedHandler } from '../api/client';
import { interactions } from '../api/interactions';
import type { AuthResponse, Me } from '../api/types';

interface AuthState {
  me: Me | null;
  /** True until a stored token has been checked against the API. */
  loading: boolean;
  signIn: (response: AuthResponse) => void;
  signOut: () => void;
  updateMe: (me: Me) => void;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [me, setMe] = useState<Me | null>(null);
  const [loading, setLoading] = useState(() => getToken() !== null);

  const signOut = useCallback(() => {
    interactions.flush();
    interactions.reset();
    setToken(null);
    setMe(null);
    queryClient.clear();
  }, [queryClient]);

  useEffect(() => {
    setUnauthorizedHandler(signOut);
    if (!getToken()) return;
    api
      .me()
      .then(setMe)
      .catch(() => signOut())
      .finally(() => setLoading(false));
  }, [signOut]);

  const signIn = useCallback(
    (response: AuthResponse) => {
      queryClient.clear();
      setToken(response.accessToken);
      setMe(response.user);
    },
    [queryClient],
  );

  const value = useMemo(() => ({ me, loading, signIn, signOut, updateMe: setMe }), [me, loading, signIn, signOut]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
