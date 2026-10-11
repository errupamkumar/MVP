import { decodePolyline, distanceM, RoutePath, type DriverState, type LatLng } from '@ecoride/core';
import { useDeviceLocation } from '@ecoride/mobile';
import { useEffect, useRef, useState } from 'react';

import { api } from './api';
import { useGpsPrefs } from './gpsPrefs';
import { useConfig } from './queries';

/** Where a simulated cab starts when nothing better is known: Baner Road, near Balewadi High Street. */
export const DEMO_START: LatLng = { lat: 18.5662, lng: 73.7801 };
const BASE_SPEED_MS = 10; // 36 km/h
const TICK_MS = 1000;
const REAL_PING_MS = 3000;
const PARKED_PING_MS = 8000;

export interface CabPosition extends LatLng {
  heading: number | null;
}

/**
 * The cab's position, sent to EcoRide while online:
 * - Real GPS: every device fix (at most every 3 s).
 * - Simulated: once a second the cab advances along the current leg (to the pickup, then to
 *   the drop) on the road route the server planned, and the fix is sent flagged `simulated`.
 *   Between trips it stays parked and keeps its fix fresh.
 */
export function useDriverGps(state: DriverState | undefined): { position: CabPosition | null; mode: 'GPS' | 'SIMULATED' } {
  const { simulate, speedX } = useGpsPrefs();
  const config = useConfig().data;
  const online = !!state && state.profile.availability !== 'OFFLINE';
  const [position, setPosition] = useState<CabPosition | null>(null);

  // ---------------------------------------------------------------- real GPS
  const lastRealPing = useRef(0);
  const onlineRef = useRef(online);
  onlineRef.current = online;
  const { fix } = useDeviceLocation(true, (f) => {
    if (simulate) return;
    setPosition({ lat: f.lat, lng: f.lng, heading: f.heading });
    if (onlineRef.current && Date.now() - lastRealPing.current >= REAL_PING_MS) {
      lastRealPing.current = Date.now();
      void api.ping({ lat: f.lat, lng: f.lng, heading: f.heading, speedKmh: f.speedKmh, accuracyM: f.accuracyM }).catch(() => undefined);
    }
  });

  // ---------------------------------------------------------------- simulation
  const sim = useRef<{ pos: CabPosition | null; legKey: string | null; path: RoutePath | null; along: number; lastPing: number }>({
    pos: null,
    legKey: null,
    path: null,
    along: 0,
    lastPing: 0,
  });
  const stateRef = useRef(state);
  stateRef.current = state;

  // Starting point: where the server last saw the cab, else the device if it is in the city, else the demo start.
  useEffect(() => {
    // Wait for the server's answer first: where it last saw the cab beats the device's own fix.
    if (!simulate || sim.current.pos || !state) return;
    const known = state.position;
    const inCity = (p: LatLng) =>
      !config || distanceM(p, { lat: config.centerLat, lng: config.centerLng }) <= config.serviceRadiusKm * 1000;
    const start = known && inCity(known) ? known : fix && inCity(fix) ? fix : DEMO_START;
    sim.current.pos = { lat: start.lat, lng: start.lng, heading: known?.heading ?? null };
    setPosition(sim.current.pos);
  }, [simulate, state, fix, config]);

  useEffect(() => {
    if (!simulate) {
      sim.current.pos = null;
      return;
    }
    const id = setInterval(() => {
      const s = sim.current;
      const st = stateRef.current;
      if (!s.pos || !st) return;
      const ride = st.ride;
      let points: LatLng[] | null = null;
      if (ride?.status === 'ACCEPTED') points = [...decodePolyline(ride.pickupPolyline), ride.pickup];
      else if (ride?.status === 'IN_PROGRESS') points = [...decodePolyline(ride.routePolyline), ride.drop];
      const legKey = ride && points ? `${ride.id}-${ride.status}` : null;
      if (legKey !== s.legKey) {
        s.legKey = legKey;
        s.path = points && points.length > 1 ? new RoutePath(points) : null;
        s.along = s.path ? s.path.locate(s.pos) : 0;
      }
      const isOnline = st.profile.availability !== 'OFFLINE';
      let moved = false;
      if (s.path && isOnline && s.along < s.path.lengthM) {
        s.along = Math.min(s.path.lengthM, s.along + (BASE_SPEED_MS * speedX * TICK_MS) / 1000);
        const p = s.path.pointAt(s.along);
        s.pos = { lat: p.lat, lng: p.lng, heading: s.path.headingAt(s.along) };
        moved = true;
        setPosition(s.pos);
      }
      const due = moved || Date.now() - s.lastPing >= PARKED_PING_MS;
      if (isOnline && due) {
        s.lastPing = Date.now();
        void api
          .ping({
            lat: s.pos.lat,
            lng: s.pos.lng,
            heading: s.pos.heading,
            speedKmh: moved ? Math.min(250, Math.round(BASE_SPEED_MS * speedX * 3.6)) : 0,
            accuracyM: 5,
            simulated: true,
          })
          .catch(() => undefined);
      }
    }, TICK_MS);
    return () => clearInterval(id);
  }, [simulate, speedX]);

  return { position: position ?? (state?.position ? { ...state.position } : null), mode: simulate ? 'SIMULATED' : 'GPS' };
}
