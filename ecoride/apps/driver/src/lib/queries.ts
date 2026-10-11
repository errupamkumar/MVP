import { useQuery } from '@tanstack/react-query';

import { api, auth } from './api';

export const keys = {
  config: ['config'] as const,
  state: ['state'] as const,
  earnings: ['earnings'] as const,
  history: ['history'] as const,
};

export function useConfig() {
  return useQuery({ queryKey: keys.config, queryFn: api.config, staleTime: Infinity });
}

/** Everything the home screen shows, every 2 seconds (offers arrive this way). */
export function useDriverState() {
  const { session } = auth.useAuth();
  return useQuery({ queryKey: keys.state, queryFn: api.state, enabled: !!session, refetchInterval: 2000 });
}

export function useEarnings() {
  return useQuery({ queryKey: keys.earnings, queryFn: api.earnings });
}

export function useHistory() {
  return useQuery({ queryKey: keys.history, queryFn: () => api.history(0) });
}
