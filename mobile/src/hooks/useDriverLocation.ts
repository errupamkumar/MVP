import { useQueryClient } from '@tanstack/react-query';
import * as Location from 'expo-location';
import { useEffect, useRef, useState } from 'react';
import { Platform } from 'react-native';

import { driverApi } from '@/api/endpoints';
import type { LocationAckDto } from '@/api/types';

import { keys } from './queries';

export interface DriverLocationState {
  permission: 'unknown' | 'granted' | 'denied' | 'unsupported';
  lastAck: LocationAckDto | null;
  /** Set when a ping landed inside the next stop's geofence and the server recorded the arrival. */
  autoArrivedAt: string | null;
}

/**
 * While the driver is on duty, shares the cab's GPS position every ~15 s or
 * 50 m. The server uses it for live tracking, overspeed alerts and geofence
 * arrival. Failures are silent: a dead spot inside the plant must never block
 * the driver's screen (the queue still works by tapping "Arrived").
 */
export function useDriverLocation(onDuty: boolean): DriverLocationState {
  const qc = useQueryClient();
  const [state, setState] = useState<DriverLocationState>({ permission: 'unknown', lastAck: null, autoArrivedAt: null });
  const subscription = useRef<Location.LocationSubscription | null>(null);

  useEffect(() => {
    if (!onDuty || Platform.OS === 'web') {
      if (Platform.OS === 'web') setState((s) => ({ ...s, permission: 'unsupported' }));
      return;
    }
    let cancelled = false;
    (async () => {
      try {
        const { status } = await Location.requestForegroundPermissionsAsync();
        if (cancelled) return;
        if (status !== 'granted') {
          setState((s) => ({ ...s, permission: 'denied' }));
          return;
        }
        setState((s) => ({ ...s, permission: 'granted' }));
        subscription.current = await Location.watchPositionAsync(
          { accuracy: Location.Accuracy.High, timeInterval: 15_000, distanceInterval: 50 },
          async (position) => {
            try {
              const speed = position.coords.speed;
              const ack = await driverApi.location({
                latitude: position.coords.latitude,
                longitude: position.coords.longitude,
                speedKmh: speed != null && speed >= 0 ? Math.round(speed * 3.6 * 10) / 10 : null,
                recordedAt: null,
              });
              setState((s) => ({ ...s, lastAck: ack, autoArrivedAt: ack.autoArrivedAt ?? s.autoArrivedAt }));
              if (ack.autoArrivedAt) {
                void qc.invalidateQueries({ queryKey: keys.driverQueue });
              }
            } catch {
              // Network gap: the next fix will be sent anyway.
            }
          },
        );
      } catch {
        if (!cancelled) setState((s) => ({ ...s, permission: 'denied' }));
      }
    })();
    return () => {
      cancelled = true;
      subscription.current?.remove();
      subscription.current = null;
    };
  }, [onDuty, qc]);

  return state;
}
