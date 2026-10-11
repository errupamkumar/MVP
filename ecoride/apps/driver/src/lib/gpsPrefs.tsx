import * as SecureStore from 'expo-secure-store';
import { createContext, ReactNode, useContext, useEffect, useMemo, useState } from 'react';
import { Platform } from 'react-native';

/**
 * Demo GPS: on a laptop (web) there is no moving car, so the app can replay a drive along
 * the real road route instead of reading the device GPS. On a phone it uses real GPS unless
 * the driver turns simulation on in Profile.
 */
export interface GpsPrefs {
  simulate: boolean;
  /** Simulated speed = 36 km/h x this. */
  speedX: number;
  setSimulate: (on: boolean) => void;
  setSpeedX: (x: number) => void;
}

const KEY = 'ecoride.driver.gps';
const DEFAULTS = { simulate: Platform.OS === 'web', speedX: 8 };
const Context = createContext<GpsPrefs | null>(null);

async function load(): Promise<typeof DEFAULTS | null> {
  try {
    const raw = Platform.OS === 'web' ? globalThis.localStorage?.getItem(KEY) : await SecureStore.getItemAsync(KEY);
    return raw ? { ...DEFAULTS, ...JSON.parse(raw) } : null;
  } catch {
    return null;
  }
}

function save(value: typeof DEFAULTS) {
  try {
    const raw = JSON.stringify(value);
    if (Platform.OS === 'web') globalThis.localStorage?.setItem(KEY, raw);
    else void SecureStore.setItemAsync(KEY, raw);
  } catch {
    // Preferences are a convenience: losing them is fine.
  }
}

export function GpsPrefsProvider({ children }: { children: ReactNode }) {
  const [prefs, setPrefs] = useState(DEFAULTS);
  useEffect(() => {
    void load().then((p) => p && setPrefs(p));
  }, []);
  const value = useMemo<GpsPrefs>(
    () => ({
      ...prefs,
      setSimulate: (simulate) =>
        setPrefs((p) => {
          const next = { ...p, simulate };
          save(next);
          return next;
        }),
      setSpeedX: (speedX) =>
        setPrefs((p) => {
          const next = { ...p, speedX };
          save(next);
          return next;
        }),
    }),
    [prefs],
  );
  return <Context.Provider value={value}>{children}</Context.Provider>;
}

export function useGpsPrefs(): GpsPrefs {
  const ctx = useContext(Context);
  if (!ctx) throw new Error('useGpsPrefs must be used inside <GpsPrefsProvider>');
  return ctx;
}
