import { createContext, ReactNode, useCallback, useContext, useMemo, useState } from 'react';

export interface StopChoice {
  id: number;
  name: string;
}

export type WhenChoice = { kind: 'now' } | { kind: 'later'; label: string; iso: string };

export interface BookingDraft {
  from: StopChoice | null;
  to: StopChoice | null;
  seats: number;
  when: WhenChoice;
  riderName: string;
  riderPhone: string;
  riderEmail: string;
  forGuest: boolean;
}

const EMPTY: BookingDraft = {
  from: null,
  to: null,
  seats: 1,
  when: { kind: 'now' },
  riderName: '',
  riderPhone: '',
  riderEmail: '',
  forGuest: false,
};

interface DraftContext {
  draft: BookingDraft;
  update: (patch: Partial<BookingDraft>) => void;
  reset: () => void;
}

const Ctx = createContext<DraftContext | null>(null);

/** The booking being composed, shared by the Book and Ride options screens. */
export function BookingDraftProvider({ children }: { children: ReactNode }) {
  const [draft, setDraft] = useState<BookingDraft>(EMPTY);
  const update = useCallback((patch: Partial<BookingDraft>) => setDraft((d) => ({ ...d, ...patch })), []);
  const reset = useCallback(() => setDraft(EMPTY), []);
  const value = useMemo(() => ({ draft, update, reset }), [draft, update, reset]);
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useBookingDraft(): DraftContext {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error('useBookingDraft must be used inside <BookingDraftProvider>');
  return ctx;
}
