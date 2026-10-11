import * as Location from 'expo-location';
import { useEffect, useRef, useState } from 'react';
import { Platform } from 'react-native';

export type LocationPermission = 'unknown' | 'granted' | 'denied' | 'unsupported';

export interface DeviceFix {
  lat: number;
  lng: number;
  accuracyM: number | null;
  speedKmh: number | null;
  /** Degrees from north; null when still or unknown. */
  heading: number | null;
  /** Date.now() when the device took the fix. */
  takenAt: number;
}

/**
 * Watches the device position while `enabled`. On web it uses the browser's geolocation
 * directly (expo-location's web watcher drops its callback); on phones expo-location.
 * Failures are silent: no signal must never block a screen.
 */
export function useDeviceLocation(enabled: boolean, onFix?: (fix: DeviceFix) => void, intervalMs = 3000) {
  const [fix, setFix] = useState<DeviceFix | null>(null);
  const [permission, setPermission] = useState<LocationPermission>('unknown');
  const onFixRef = useRef(onFix);
  useEffect(() => {
    onFixRef.current = onFix;
  }, [onFix]);

  useEffect(() => {
    if (!enabled) return;
    let cancelled = false;
    let stop: (() => void) | null = null;
    const deliver = (f: DeviceFix) => {
      if (cancelled) return;
      setFix(f);
      onFixRef.current?.(f);
    };
    if (Platform.OS === 'web') {
      const geo = (globalThis.navigator as Navigator | undefined)?.geolocation;
      if (!geo) {
        setPermission('unsupported');
        return;
      }
      const id = geo.watchPosition(
        (p) => {
          setPermission('granted');
          deliver(fromCoords(p.coords, p.timestamp));
        },
        (err) => {
          if (err.code === err.PERMISSION_DENIED) setPermission('denied');
        },
        { enableHighAccuracy: true, maximumAge: 5000, timeout: 20000 },
      );
      stop = () => geo.clearWatch(id);
    } else {
      (async () => {
        try {
          const { status } = await Location.requestForegroundPermissionsAsync();
          if (cancelled) return;
          if (status !== 'granted') {
            setPermission('denied');
            return;
          }
          setPermission('granted');
          const sub = await Location.watchPositionAsync(
            { accuracy: Location.Accuracy.High, timeInterval: intervalMs, distanceInterval: 0 },
            (p) => deliver(fromCoords(p.coords, p.timestamp)),
          );
          if (cancelled) sub.remove();
          else stop = () => sub.remove();
        } catch {
          if (!cancelled) setPermission('denied');
        }
      })();
    }
    return () => {
      cancelled = true;
      stop?.();
    };
  }, [enabled, intervalMs]);

  return { fix, permission };
}

/** One fix, or null within the timeout. */
export async function currentFix(timeoutMs = 8000): Promise<DeviceFix | null> {
  if (Platform.OS === 'web') {
    const geo = (globalThis.navigator as Navigator | undefined)?.geolocation;
    if (!geo) return null;
    return new Promise((resolve) => {
      const timer = setTimeout(() => resolve(null), timeoutMs);
      geo.getCurrentPosition(
        (p) => {
          clearTimeout(timer);
          resolve(fromCoords(p.coords, p.timestamp));
        },
        () => {
          clearTimeout(timer);
          resolve(null);
        },
        { enableHighAccuracy: true, maximumAge: 10000, timeout: timeoutMs },
      );
    });
  }
  try {
    const { status } = await Location.requestForegroundPermissionsAsync();
    if (status !== 'granted') return null;
    const p = await Promise.race([
      Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.High }),
      new Promise<null>((resolve) => setTimeout(() => resolve(null), timeoutMs)),
    ]);
    return p ? fromCoords(p.coords, p.timestamp) : null;
  } catch {
    return null;
  }
}

function fromCoords(
  c: { latitude: number; longitude: number; accuracy: number | null; speed: number | null; heading: number | null },
  timestamp: number,
): DeviceFix {
  const moving = c.speed != null && c.speed > 0.5;
  return {
    lat: c.latitude,
    lng: c.longitude,
    accuracyM: c.accuracy != null && c.accuracy >= 0 ? Math.min(5000, Math.round(c.accuracy)) : null,
    speedKmh: c.speed != null && c.speed >= 0 ? Math.min(250, Math.round(c.speed * 36) / 10) : null,
    heading: moving && c.heading != null && c.heading >= 0 ? Math.round(c.heading) % 360 : null,
    takenAt: Math.min(timestamp || Date.now(), Date.now()),
  };
}
