import { distanceM, type LatLng } from '@ecoride/core';
import { useDeviceLocation } from '@ecoride/mobile';
import { useEffect, useState } from 'react';

import { useConfig } from './queries';

/** Where the demo puts a rider whose phone is outside Pune (or gives no fix): Balewadi High Street. */
export const DEMO_POSITION: LatLng = { lat: 18.570719, lng: 73.774308 };

export type PositionSource = 'GPS' | 'DEMO' | 'LOCATING';

/**
 * The rider's position for the map and the default pickup: the phone's GPS when it is inside
 * the service area, otherwise a demo position in Pune (and the app says so).
 */
export function useRiderPosition(): { position: LatLng; source: PositionSource; accuracyM: number | null } {
  const { fix, permission } = useDeviceLocation(true);
  const config = useConfig().data;
  const [waitedLong, setWaitedLong] = useState(false);
  useEffect(() => {
    const t = setTimeout(() => setWaitedLong(true), 6000);
    return () => clearTimeout(t);
  }, []);

  if (fix && config) {
    const inside = distanceM(fix, { lat: config.centerLat, lng: config.centerLng }) <= config.serviceRadiusKm * 1000;
    if (inside) return { position: { lat: fix.lat, lng: fix.lng }, source: 'GPS', accuracyM: fix.accuracyM };
    return { position: DEMO_POSITION, source: 'DEMO', accuracyM: null };
  }
  if (permission === 'denied' || permission === 'unsupported' || waitedLong) {
    return { position: DEMO_POSITION, source: 'DEMO', accuracyM: null };
  }
  return { position: DEMO_POSITION, source: 'LOCATING', accuracyM: null };
}
