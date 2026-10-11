import { useQueryClient } from '@tanstack/react-query';
import { createContext, ReactNode, useCallback, useContext, useEffect, useMemo, useState } from 'react';

import { AdminSession, api, loadSession, setSession, setUnauthorizedHandler } from './api';

interface AuthState {
  session: AdminSession | null;
  notice: string | null;
  signIn: (email: string, password: string) => Promise<void>;
  signOut: (notice?: string) => void;
}

const Ctx = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [session, setState] = useState<AdminSession | null>(loadSession);
  const [notice, setNotice] = useState<string | null>(null);

  const signOut = useCallback(
    (why?: string) => {
      setSession(null);
      setState(null);
      setNotice(why ?? null);
      queryClient.clear();
    },
    [queryClient],
  );

  useEffect(() => {
    setUnauthorizedHandler(() => signOut('Your session ended. Please sign in again.'));
  }, [signOut]);

  const signIn = useCallback(async (email: string, password: string) => {
    const res = await api.login(email, password);
    const next = { token: res.token, expiresAt: res.expiresAt, admin: res.admin };
    setSession(next);
    setState(next);
    setNotice(null);
  }, []);

  const value = useMemo(() => ({ session, notice, signIn, signOut }), [session, notice, signIn, signOut]);
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error('useAuth must be inside AuthProvider');
  return ctx;
}
