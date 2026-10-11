import type { Place } from '@ecoride/core';
import { createContext, ReactNode, useContext, useMemo, useState } from 'react';

/** The trip being planned: pickup and drop, shared by "Where to?" and "Choose a ride". */
interface Booking {
  pickup: Place | null;
  /** The pickup was set from the phone's location (it follows the rider until they pick another). */
  pickupIsCurrent: boolean;
  drop: Place | null;
  setPickup: (place: Place | null, isCurrent?: boolean) => void;
  setDrop: (place: Place | null) => void;
  reset: () => void;
}

const BookingContext = createContext<Booking | null>(null);

export function BookingProvider({ children }: { children: ReactNode }) {
  const [pickup, setPickupState] = useState<Place | null>(null);
  const [pickupIsCurrent, setPickupIsCurrent] = useState(true);
  const [drop, setDrop] = useState<Place | null>(null);
  const value = useMemo<Booking>(
    () => ({
      pickup,
      pickupIsCurrent,
      drop,
      setPickup: (place, isCurrent = false) => {
        setPickupState(place);
        setPickupIsCurrent(isCurrent);
      },
      setDrop,
      reset: () => {
        setDrop(null);
        setPickupIsCurrent(true);
      },
    }),
    [pickup, pickupIsCurrent, drop],
  );
  return <BookingContext.Provider value={value}>{children}</BookingContext.Provider>;
}

export function useBooking(): Booking {
  const ctx = useContext(BookingContext);
  if (!ctx) throw new Error('useBooking must be used inside <BookingProvider>');
  return ctx;
}
