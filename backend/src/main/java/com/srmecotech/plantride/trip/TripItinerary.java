package com.srmecotech.plantride.trip;

import com.srmecotech.plantride.booking.Booking;
import com.srmecotech.plantride.booking.BookingStatus;
import com.srmecotech.plantride.masterdata.RoutePlan;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Where a trip goes next. A cab stops only where someone boards or leaves;
 * other stops on the route are driven past.
 */
public final class TripItinerary {

    private TripItinerary() {
    }

    /** The next route position with a pickup (ASSIGNED) or drop (ONBOARD), or null when none remain. */
    public static Integer nextStoppingSeq(Trip trip, Collection<Booking> activeRiders) {
        int floor = trip.getCurrentSeq() == null ? trip.getStartSeq() - 1 : trip.getCurrentSeq();
        return activeRiders.stream()
                .map(b -> b.getStatus() == BookingStatus.ASSIGNED ? b.getFromSeq() : b.getToSeq())
                .filter(seq -> seq > floor)
                .min(Integer::compareTo)
                .orElse(null);
    }

    /** Riders still waiting to board at (or before) the cab's current stop. */
    public static List<Booking> waitingAtCurrentStop(Trip trip, Collection<Booking> activeRiders) {
        if (trip.getCurrentSeq() == null) {
            return List.of();
        }
        int current = trip.getCurrentSeq();
        return activeRiders.stream()
                .filter(b -> b.getStatus() == BookingStatus.ASSIGNED && b.getFromSeq() <= current)
                .toList();
    }

    /** Why the driver cannot move to the next stop yet; empty when they can. */
    public static Optional<String> arrivalBlocker(Trip trip, RoutePlan plan, Collection<Booking> activeRiders) {
        List<Booking> waiting = waitingAtCurrentStop(trip, activeRiders);
        if (!waiting.isEmpty()) {
            String names = waiting.stream().map(Booking::getRiderName).collect(Collectors.joining(", "));
            return Optional.of("Board or mark no-show for " + names + " at "
                    + plan.stopAt(trip.getCurrentSeq()).getName() + " first.");
        }
        if (nextStoppingSeq(trip, activeRiders) == null) {
            return Optional.of("There are no more stops on this trip.");
        }
        return Optional.empty();
    }
}
