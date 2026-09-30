import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';

import type { UserSummary } from '@/api/types';

export interface StoredSession {
  token: string;
  expiresAt: string;
  user: UserSummary;
}

const KEY = 'plant-ride.session';

/**
 * The JWT lives in the device keystore (SecureStore). On web there is no
 * keystore, so it falls back to localStorage; every access is guarded
 * because storage can be blocked (private mode, disabled site data).
 */
export const sessionStorage = {
  async load(): Promise<StoredSession | null> {
    try {
      const raw = Platform.OS === 'web' ? globalThis.localStorage?.getItem(KEY) : await SecureStore.getItemAsync(KEY);
      return raw ? (JSON.parse(raw) as StoredSession) : null;
    } catch {
      return null;
    }
  },

  async save(session: StoredSession): Promise<void> {
    const raw = JSON.stringify(session);
    try {
      if (Platform.OS === 'web') {
        globalThis.localStorage?.setItem(KEY, raw);
      } else {
        await SecureStore.setItemAsync(KEY, raw);
      }
    } catch {
      // Not fatal: the session still works until the app is closed.
    }
  },

  async clear(): Promise<void> {
    try {
      if (Platform.OS === 'web') {
        globalThis.localStorage?.removeItem(KEY);
      } else {
        await SecureStore.deleteItemAsync(KEY);
      }
    } catch {
      // Ignore: nothing to clear.
    }
  },
};
