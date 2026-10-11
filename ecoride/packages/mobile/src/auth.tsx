import { useQueryClient } from '@tanstack/react-query';
import * as SecureStore from 'expo-secure-store';
import { createContext, ReactNode, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { Platform } from 'react-native';

export interface Session<P> {
  token: string;
  expiresAt: string;
  profile: P;
}

export interface AuthState<P> {
  /** False until the stored session has been read (screens wait instead of flashing the sign-in page). */
  ready: boolean;
  session: Session<P> | null;
  /** Why the user was signed out, shown on the sign-in screen. */
  notice: string | null;
  signIn: (session: Session<P>) => Promise<void>;
  signOut: (notice?: string) => Promise<void>;
  setProfile: (profile: P) => void;
}

/**
 * The token lives in the device keystore (SecureStore); on web, in localStorage. Every access
 * is guarded because storage can be blocked (private mode).
 */
function store(key: string) {
  return {
    async load<P>(): Promise<Session<P> | null> {
      try {
        const raw = Platform.OS === 'web' ? globalThis.localStorage?.getItem(key) : await SecureStore.getItemAsync(key);
        return raw ? (JSON.parse(raw) as Session<P>) : null;
      } catch {
        return null;
      }
    },
    async save<P>(session: Session<P>): Promise<void> {
      try {
        const raw = JSON.stringify(session);
        if (Platform.OS === 'web') globalThis.localStorage?.setItem(key, raw);
        else await SecureStore.setItemAsync(key, raw);
      } catch {
        // Not fatal: the session lasts until the app closes.
      }
    },
    async clear(): Promise<void> {
      try {
        if (Platform.OS === 'web') globalThis.localStorage?.removeItem(key);
        else await SecureStore.deleteItemAsync(key);
      } catch {
        // Nothing to clear.
      }
    },
  };
}

/**
 * One auth module per app (the rider and driver apps keep separate sessions, even on the
 * same device). `getToken` and `notifyUnauthorized` are for the API client, outside React.
 */
export function createAuth<P>(storageKey: string) {
  const sessionStore = store(storageKey);
  let currentToken: string | null = null;
  let unauthorized: () => void = () => undefined;
  const Context = createContext<AuthState<P> | null>(null);

  function AuthProvider({ children }: { children: ReactNode }) {
    const queryClient = useQueryClient();
    const [ready, setReady] = useState(false);
    const [session, setSession] = useState<Session<P> | null>(null);
    const [notice, setNotice] = useState<string | null>(null);

    const apply = useCallback((next: Session<P> | null) => {
      currentToken = next?.token ?? null;
      setSession(next);
    }, []);

    const signOut = useCallback(
      async (why?: string) => {
        apply(null);
        setNotice(why ?? null);
        queryClient.clear();
        await sessionStore.clear();
      },
      [apply, queryClient],
    );

    useEffect(() => {
      unauthorized = () => {
        if (currentToken) void signOut('Your session ended. Please sign in again.');
      };
    }, [signOut]);

    useEffect(() => {
      let cancelled = false;
      (async () => {
        const stored = await sessionStore.load<P>();
        if (!cancelled && stored && new Date(stored.expiresAt).getTime() > Date.now()) {
          apply(stored);
        } else if (stored) {
          await sessionStore.clear();
        }
        if (!cancelled) setReady(true);
      })();
      return () => {
        cancelled = true;
      };
    }, [apply]);

    const signIn = useCallback(
      async (next: Session<P>) => {
        queryClient.clear();
        apply(next);
        setNotice(null);
        await sessionStore.save(next);
      },
      [apply, queryClient],
    );

    const setProfile = useCallback((profile: P) => {
      setSession((s) => {
        if (!s) return s;
        const next = { ...s, profile };
        void sessionStore.save(next);
        return next;
      });
    }, []);

    const value = useMemo<AuthState<P>>(
      () => ({ ready, session, notice, signIn, signOut, setProfile }),
      [ready, session, notice, signIn, signOut, setProfile],
    );
    return <Context.Provider value={value}>{children}</Context.Provider>;
  }

  function useAuth(): AuthState<P> {
    const ctx = useContext(Context);
    if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
    return ctx;
  }

  return {
    AuthProvider,
    useAuth,
    getToken: () => currentToken,
    notifyUnauthorized: () => unauthorized(),
  };
}
