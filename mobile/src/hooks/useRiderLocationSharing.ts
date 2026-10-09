import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useRef, useState } from 'react';

import { riderApi } from '@/api/endpoints';
import type { RiderLocationAckDto } from '@/api/types';

import { keys } from './queries';
import { type DeviceFix, fixAgeSeconds, type LocationPermission, useLocationWatcher } from './useLocationWatcher';

/** Rides the rider turned sharing off for; remembered for the rest of the app session. */
const stoppedFor = new Set<number>();

export interface RiderSharingState {
  permission: LocationPermission;
  /** The rider turned sharing off for this ride. */
  stopped: boolean;
  lastAck: RiderLocationAckDto | null;
  stop: () => void;
  resume: () => void;
}

/**
 * While the rider's cab is on its way, shares where they are with its
 * driver every ~15 s (and at least every 30 s while standing still), so the
 * driver can find them at a busy gate. Asking for the phone's permission is
 * the consent; the rider can stop at any time, which deletes the position on
 * the server. Sharing ends by itself at boarding.
 */
export function useRiderLocationSharing(bookingId: number, waitingForCab: boolean): RiderSharingState {
  const qc = useQueryClient();
  const [stopped, setStopped] = useState(() => stoppedFor.has(bookingId));
  const [lastAck, setLastAck] = useState<RiderLocationAckDto | null>(null);
  const inFlight = useRef(false);
  const stoppedRef = useRef(stopped);
  useEffect(() => {
    stoppedRef.current = stopped;
  }, [stopped]);

  const send = useCallback(
    async (fix: DeviceFix) => {
      if (inFlight.current || stoppedRef.current) return;
      inFlight.current = true;
      try {
        const ack = await riderApi.shareLocation(bookingId, {
          latitude: fix.latitude,
          longitude: fix.longitude,
          accuracyM: fix.accuracyM,
          ageSeconds: fixAgeSeconds(fix),
        });
        setLastAck(ack);
      } catch {
        // Boarded or cancelled meanwhile, or a network gap: the booking poll ends sharing when it should.
      } finally {
        inFlight.current = false;
        // Stopped while this fix was on its way: make sure it does not linger on the server.
        if (stoppedRef.current) void riderApi.stopSharingLocation(bookingId).catch(() => undefined);
      }
    },
    [bookingId],
  );

  const permission = useLocationWatcher(waitingForCab && !stopped, send, { intervalMs: 15_000, refreshMs: 30_000 });

  const stop = useCallback(() => {
    stoppedFor.add(bookingId);
    stoppedRef.current = true;
    setStopped(true);
    setLastAck(null);
    riderApi
      .stopSharingLocation(bookingId)
      .catch(() => undefined)
      .finally(() => void qc.invalidateQueries({ queryKey: keys.booking(bookingId) }));
  }, [bookingId, qc]);

  const resume = useCallback(() => {
    stoppedFor.delete(bookingId);
    stoppedRef.current = false;
    setStopped(false);
  }, [bookingId]);

  return { permission, stopped, lastAck, stop, resume };
}
