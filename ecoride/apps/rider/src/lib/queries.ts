import type { LatLng, RideStatus } from '@ecoride/core';
import { useQuery } from '@tanstack/react-query';

import { api, auth } from './api';

const TERMINAL: RideStatus[] = ['COMPLETED', 'CANCELLED', 'NO_DRIVERS'];

export const keys = {
  config: ['config'] as const,
  me: ['me'] as const,
  places: ['places'] as const,
  wallet: ['wallet'] as const,
  current: ['ride', 'current'] as const,
  ride: (id: number) => ['ride', id] as const,
  history: ['rides', 'history'] as const,
};

export function useConfig() {
  return useQuery({ queryKey: keys.config, queryFn: api.config, staleTime: Infinity });
}

export function useMe() {
  const { session } = auth.useAuth();
  return useQuery({ queryKey: keys.me, queryFn: api.me, enabled: !!session, initialData: session?.profile, staleTime: 10_000 });
}

export function usePlaces() {
  return useQuery({ queryKey: keys.places, queryFn: api.places });
}

export function useWallet() {
  return useQuery({ queryKey: keys.wallet, queryFn: api.wallet });
}

/** Free cars around the rider (refreshed every 5 s, so they move on the map). */
export function useNearby(at: LatLng | null) {
  return useQuery({
    queryKey: ['nearby', at ? `${at.lat.toFixed(4)},${at.lng.toFixed(4)}` : 'none'],
    queryFn: () => api.nearby(at!),
    enabled: !!at,
    refetchInterval: 5000,
    placeholderData: (prev) => prev,
  });
}

export function useCurrentRide() {
  return useQuery({ queryKey: keys.current, queryFn: api.current, refetchInterval: 6000 });
}

/** The live ride: every 2 s until it ends. */
export function useRide(id: number) {
  return useQuery({
    queryKey: keys.ride(id),
    queryFn: () => api.ride(id),
    enabled: Number.isFinite(id),
    refetchInterval: (q) => (q.state.data && TERMINAL.includes(q.state.data.status) ? false : 2000),
  });
}

export function useHistory() {
  return useQuery({ queryKey: keys.history, queryFn: () => api.history(0) });
}
