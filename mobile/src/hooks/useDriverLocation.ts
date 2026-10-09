import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useRef, useState } from 'react';

import { driverApi } from '@/api/endpoints';
import type { LocationAckDto } from '@/api/types';

import { keys } from './queries';
import { type DeviceFix, fixAgeSeconds, type LocationPermission, useLocationWatcher } from './useLocationWatcher';

export interface DriverLocationState {
  permission: LocationPermission;
  lastAck: LocationAckDto | null;
  /** Date.now() of the last ping the server accepted. */
  lastSentAt: number | null;
  /** Set when a ping landed inside the next stop's geofence and the server recorded the arrival. */
  autoArrivedAt: string | null;
}

/**
 * While the driver is on duty, shares the cab's position every ~10 s (and
 * at least every 30 s while parked). The server uses it to show riders where
 * their cab is, for overspeed alerts and for geofence arrival. Failures are
 * silent: a dead spot inside the plant must never block the driver's screen
 * (the queue still works by tapping "Arrived"), and the next fix retries.
 */
export function useDriverLocation(onDuty: boolean): DriverLocationState {
  const qc = useQueryClient();
  const [state, setState] = useState<Omit<DriverLocationState, 'permission'>>({
    lastAck: null,
    lastSentAt: null,
    autoArrivedAt: null,
  });
  const inFlight = useRef(false);
  const shared = useRef(false);

  const send = useCallback(
    async (fix: DeviceFix) => {
      // A slow network must not pile up requests; the next fix carries a newer position anyway.
      if (inFlight.current) return;
      inFlight.current = true;
      try {
        const ack = await driverApi.location({
          latitude: fix.latitude,
          longitude: fix.longitude,
          speedKmh: fix.speedKmh,
          accuracyM: fix.accuracyM,
          heading: fix.heading,
          ageSeconds: fixAgeSeconds(fix),
          recordedAt: null,
        });
        const first = !shared.current && !ack.outsideServiceArea;
        if (!ack.outsideServiceArea) shared.current = true;
        setState((s) => ({
          lastAck: ack,
          lastSentAt: ack.outsideServiceArea ? s.lastSentAt : Date.now(),
          autoArrivedAt: ack.autoArrivedAt ?? s.autoArrivedAt,
        }));
        // The first fix moves the cab on the map now, not at the next queue poll.
        if (first) void qc.invalidateQueries({ queryKey: keys.driverQueue });
        // An arrival recorded by GPS changes the queue, and a closed trip changes the duty totals.
        if (ack.autoArrivedAt) void qc.invalidateQueries({ queryKey: ['driver'] });
      } catch {
        // Network gap: the next fix is sent anyway.
      } finally {
        inFlight.current = false;
      }
    },
    [qc],
  );

  const permission = useLocationWatcher(onDuty, send, { intervalMs: 10_000, refreshMs: 30_000 });
  return { permission, ...state };
}
