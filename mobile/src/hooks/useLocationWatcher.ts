import * as Location from 'expo-location';
import { useEffect, useRef, useState } from 'react';
import { Platform } from 'react-native';

export type LocationPermission = 'unknown' | 'granted' | 'denied' | 'unsupported';

export interface DeviceFix {
  latitude: number;
  longitude: number;
  accuracyM: number | null;
  speedKmh: number | null;
  /** Degrees from north; null when the device is still or cannot tell. */
  heading: number | null;
  /** Date.now() when the device took the fix. */
  takenAt: number;
}

/** Seconds since the device took the fix: sent instead of a timestamp, so the phone's clock and time zone do not matter. */
export const fixAgeSeconds = (fix: DeviceFix) => Math.max(0, Math.round((Date.now() - fix.takenAt) / 1000));

interface WatchOptions {
  /** Ask the device for a fix at least this often. */
  intervalMs: number;
  /** When nothing was delivered for this long (a phone lying still sends nothing on iOS or web), take a fresh fix. */
  refreshMs: number;
}

/**
 * Watches the device position while `enabled` and hands every fix to
 * `onFix`. When the device has been quiet for `refreshMs` it asks for a
 * fresh fix, so a parked cab or a rider standing at the gate still reads as
 * live. On web it uses the browser's geolocation directly (expo-location's
 * web watcher drops its callback when the browser's watch id differs from
 * its own). Failures are silent: no signal must never block a screen.
 */
export function useLocationWatcher(
  enabled: boolean,
  onFix: (fix: DeviceFix) => void,
  { intervalMs, refreshMs }: WatchOptions,
): LocationPermission {
  const [permission, setPermission] = useState<LocationPermission>('unknown');
  const onFixRef = useRef(onFix);
  useEffect(() => {
    onFixRef.current = onFix;
  }, [onFix]);

  useEffect(() => {
    if (!enabled) return;
    let cancelled = false;
    let lastDeliveredAt = 0;
    let refreshing = false;
    let stopWatching: (() => void) | null = null;

    const deliver = (fix: DeviceFix) => {
      if (cancelled) return;
      lastDeliveredAt = Date.now();
      onFixRef.current(fix);
    };

    const web = Platform.OS === 'web';
    const geo = web ? (globalThis.navigator as Navigator | undefined)?.geolocation : undefined;
    if (web && !geo) {
      setPermission('unsupported');
      return;
    }

    const refresh = async () => {
      if (refreshing) return;
      refreshing = true;
      try {
        if (geo) {
          await new Promise<void>((resolve) =>
            geo.getCurrentPosition(
              (p) => {
                deliver(fromBrowser(p));
                resolve();
              },
              () => resolve(),
              { enableHighAccuracy: true, maximumAge: 5_000, timeout: 20_000 },
            ),
          );
        } else {
          deliver(fromExpo(await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.High })));
        }
      } catch {
        // No fix right now (indoors, no signal): try again on the next tick.
      } finally {
        refreshing = false;
      }
    };

    if (geo) {
      const id = geo.watchPosition(
        (p) => {
          setPermission('granted');
          deliver(fromBrowser(p));
        },
        (err) => {
          if (err.code === err.PERMISSION_DENIED) setPermission('denied');
        },
        { enableHighAccuracy: true, maximumAge: 5_000, timeout: 30_000 },
      );
      stopWatching = () => geo.clearWatch(id);
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
            // distanceInterval 0: Android then reports on the interval even while standing still.
            { accuracy: Location.Accuracy.High, timeInterval: intervalMs, distanceInterval: 0 },
            (p) => deliver(fromExpo(p)),
          );
          if (cancelled) sub.remove();
          else stopWatching = () => sub.remove();
        } catch {
          if (!cancelled) setPermission('denied');
        }
      })();
    }

    const tick = setInterval(() => {
      if (lastDeliveredAt > 0 && Date.now() - lastDeliveredAt >= refreshMs) void refresh();
    }, 5_000);

    return () => {
      cancelled = true;
      clearInterval(tick);
      stopWatching?.();
    };
  }, [enabled, intervalMs, refreshMs]);

  return enabled ? permission : 'unknown';
}

function fromExpo(p: Location.LocationObject): DeviceFix {
  return fix(p.coords.latitude, p.coords.longitude, p.coords.accuracy, p.coords.speed, p.coords.heading, p.timestamp);
}

function fromBrowser(p: GeolocationPosition): DeviceFix {
  return fix(p.coords.latitude, p.coords.longitude, p.coords.accuracy, p.coords.speed, p.coords.heading, p.timestamp);
}

function fix(
  latitude: number,
  longitude: number,
  accuracy: number | null,
  speedMs: number | null,
  heading: number | null,
  timestamp: number,
): DeviceFix {
  const moving = speedMs != null && speedMs > 0.5;
  return {
    latitude,
    longitude,
    accuracyM: accuracy != null && accuracy >= 0 ? Math.min(5000, Math.round(accuracy)) : null,
    speedKmh: speedMs != null && speedMs >= 0 ? Math.min(250, Math.round(speedMs * 3.6 * 10) / 10) : null,
    // A heading means nothing while standing still; the server accepts 0-359.
    heading: moving && heading != null && heading >= 0 ? Math.round(heading) % 360 : null,
    // Some devices report a timestamp from the future (clock skew); never trust it past now.
    takenAt: Math.min(timestamp || Date.now(), Date.now()),
  };
}
