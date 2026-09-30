import { useQueryClient } from '@tanstack/react-query';
import { createContext, ReactNode, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';

import { configureAuth } from '@/api/client';
import { authApi } from '@/api/endpoints';
import type { Role, UserSummary } from '@/api/types';
import { parseInstant } from '@/lib/format';

import { sessionStorage, StoredSession } from './storage';

interface AuthState {
  /** False until the stored session has been read; screens wait on it instead of flashing the login page. */
  ready: boolean;
  user: UserSummary | null;
  /** Set when the server rejected the token, so the login screen can say why. */
  sessionNotice: string | null;
  signIn: (username: string, password: string) => Promise<UserSummary>;
  signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export function homePathFor(role: Role): '/rider' | '/driver' | '/desk' {
  switch (role) {
    case 'EMPLOYEE':
      return '/rider';
    case 'DRIVER':
      return '/driver';
    default:
      return '/desk';
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [ready, setReady] = useState(false);
  const [session, setSession] = useState<StoredSession | null>(null);
  const [sessionNotice, setSessionNotice] = useState<string | null>(null);
  // The axios interceptor reads the token synchronously, outside React.
  const sessionRef = useRef<StoredSession | null>(null);

  const applySession = useCallback((next: StoredSession | null) => {
    sessionRef.current = next;
    setSession(next);
  }, []);

  const signOut = useCallback(async () => {
    applySession(null);
    queryClient.clear();
    await sessionStorage.clear();
  }, [applySession, queryClient]);

  useEffect(() => {
    configureAuth(
      () => sessionRef.current?.token ?? null,
      () => {
        if (sessionRef.current) {
          setSessionNotice('Your session expired. Please sign in again.');
          void signOut();
        }
      },
    );
  }, [signOut]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const stored = await sessionStorage.load();
      if (!cancelled && stored && parseInstant(stored.expiresAt).getTime() > Date.now()) {
        applySession(stored);
      } else if (stored) {
        await sessionStorage.clear();
      }
      if (!cancelled) setReady(true);
    })();
    return () => {
      cancelled = true;
    };
  }, [applySession]);

  const signIn = useCallback(
    async (username: string, password: string) => {
      const response = await authApi.login(username.trim(), password);
      const next: StoredSession = { token: response.token, expiresAt: response.expiresAt, user: response.user };
      queryClient.clear();
      applySession(next);
      setSessionNotice(null);
      await sessionStorage.save(next);
      return response.user;
    },
    [applySession, queryClient],
  );

  const value = useMemo<AuthState>(
    () => ({ ready, user: session?.user ?? null, sessionNotice, signIn, signOut }),
    [ready, session, sessionNotice, signIn, signOut],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used inside <AuthProvider>');
  }
  return ctx;
}
