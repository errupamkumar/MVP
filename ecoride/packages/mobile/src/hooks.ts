import { useEffect, useRef, useState } from 'react';

/** Re-renders every `intervalMs` and returns Date.now(), for countdowns and timers. */
export function useNow(intervalMs = 1000): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), intervalMs);
    return () => clearInterval(id);
  }, [intervalMs]);
  return now;
}

/**
 * A server-computed number of seconds, counted down locally from when it was received,
 * so the phone's clock and time zone never matter.
 */
export function useCountdown(serverSeconds: number | null | undefined, receivedAt: number): number | null {
  const now = useNow(250);
  if (serverSeconds == null) return null;
  return Math.max(0, serverSeconds - (now - receivedAt) / 1000);
}

/** Counts up from a server-computed number of seconds (waiting time, search time). */
export function useElapsed(serverSeconds: number | null | undefined, receivedAt: number): number | null {
  const now = useNow(1000);
  if (serverSeconds == null) return null;
  return serverSeconds + (now - receivedAt) / 1000;
}

/** `value`, once it has stopped changing for `ms` (search-as-you-type). */
export function useDebounced<T>(value: T, ms = 300): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), ms);
    return () => clearTimeout(t);
  }, [value, ms]);
  return debounced;
}

/** The moment `value` last changed (identity), for useCountdown. */
export function useReceivedAt(value: unknown): number {
  const ref = useRef({ value, at: Date.now() });
  if (ref.current.value !== value) {
    ref.current = { value, at: Date.now() };
  }
  return ref.current.at;
}
