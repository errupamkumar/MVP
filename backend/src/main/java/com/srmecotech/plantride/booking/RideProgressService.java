package com.srmecotech.plantride.booking;

import com.srmecotech.plantride.booking.dto.RiderDtos.ProgressStopDto;
import com.srmecotech.plantride.booking.dto.RiderDtos.RideProgressDto;
import com.srmecotech.plantride.masterdata.RoutePlan;
import com.srmecotech.plantride.masterdata.RouteResolver;
import com.srmecotech.plantride.matching.EtaCalculator;
import com.srmecotech.plantride.matching.SeatLoad;
import com.srmecotech.plantride.trip.Trip;
import com.srmecotech.plantride.trip.TripItinerary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Where a rider's cab is and when it gets there. Shared by the rider app
 * and the public tracking link so both always tell the same story.
 */
@Service
public class RideProgressService {

    private final BookingRepository bookingRepository;
    private final RouteResolver routeResolver;
    private final EtaCalculator etaCalculator;

    public RideProgressService(BookingRepository bookingRepository, RouteResolver routeResolver,
                               EtaCalculator etaCalculator) {
        this.bookingRepository = bookingRepository;
        this.routeResolver = routeResolver;
        this.etaCalculator = etaCalculator;
    }

    @Transactional(readOnly = true)
    public RideProgressDto progress(Booking booking) {
        RoutePlan plan = routeResolver.planFor(booking.getRoute().getId());
        Trip trip = booking.getTrip();
        String[] stage = stage(booking, trip);

        if (trip == null) {
            List<ProgressStopDto> stops = List.of(
                    new ProgressStopDto(booking.getFromSeq(), plan.stopAt(booking.getFromSeq()).getName(), true, false,
                            BookingStatus.CLOSED.contains(booking.getStatus()) ? "PASSED" : "AHEAD"),
                    new ProgressStopDto(booking.getToSeq(), plan.stopAt(booking.getToSeq()).getName(), false, true,
                            BookingStatus.CLOSED.contains(booking.getStatus()) ? "PASSED" : "AHEAD"));
            return new RideProgressDto(stage[0], stage[1], null, null, null, 0, 0,
                    plan.route().getMaxPassengers(), stops);
        }

        List<Booking> onTrip = bookingRepository.findOnTrip(trip.getId(),
                EnumSet.of(BookingStatus.ASSIGNED, BookingStatus.ONBOARD, BookingStatus.COMPLETED));
        List<Booking> active = onTrip.stream().filter(b -> BookingStatus.ON_TRIP.contains(b.getStatus())).toList();
        Set<Integer> stopping = new TreeSet<>();
        active.forEach(b -> {
            stopping.add(b.getFromSeq());
            stopping.add(b.getToSeq());
        });

        Integer etaPickup = null;
        Integer etaDrop = null;
        if (booking.getStatus() == BookingStatus.ASSIGNED) {
            etaPickup = etaCalculator.minutesUntil(trip, plan, booking.getFromSeq(), stopping);
            etaDrop = etaCalculator.minutesUntil(trip, plan, booking.getToSeq(), stopping);
        } else if (booking.getStatus() == BookingStatus.ONBOARD) {
            etaDrop = etaCalculator.minutesUntil(trip, plan, booking.getToSeq(), stopping);
        }

        // Stops shown: every stop on this run up to the rider's drop.
        Set<Integer> shown = new TreeSet<>();
        onTrip.forEach(b -> {
            shown.add(b.getFromSeq());
            shown.add(b.getToSeq());
        });
        shown.add(booking.getFromSeq());
        shown.add(booking.getToSeq());
        Integer current = trip.getCurrentSeq();
        Integer next = TripItinerary.nextStoppingSeq(trip, active);
        boolean closed = BookingStatus.CLOSED.contains(booking.getStatus());
        List<ProgressStopDto> stops = shown.stream()
                .filter(seq -> seq <= booking.getToSeq())
                .map(seq -> new ProgressStopDto(seq, plan.stopAt(seq).getName(),
                        seq == booking.getFromSeq(), seq == booking.getToSeq(),
                        closed ? "PASSED"
                                : current == null ? (next != null && seq.equals(next) ? "NEXT" : "AHEAD")
                                : seq < current ? "PASSED"
                                : seq.equals(current) ? "HERE"
                                : next != null && seq.equals(next) ? "NEXT" : "AHEAD"))
                .toList();

        int coRiders = (int) onTrip.stream()
                .filter(b -> !b.getId().equals(booking.getId()))
                .filter(b -> b.getFromSeq() < booking.getToSeq() && booking.getFromSeq() < b.getToSeq())
                .count();
        List<Booking> others = onTrip.stream().filter(b -> !b.getId().equals(booking.getId())).toList();
        int seatsTaken = SeatLoad.maxLoad(others, booking.getFromSeq(), booking.getToSeq()) + booking.getSeats();
        int capacity = trip.isExclusiveRide()
                ? trip.getVehicle().getSeatCapacity()
                : Math.min(trip.getRoute().getMaxPassengers(), trip.getVehicle().getSeatCapacity());
        String vehicleAt = current != null ? plan.stopAt(current).getName()
                : trip.getVehicle().getCurrentStop() == null ? null : trip.getVehicle().getCurrentStop().getName();
        return new RideProgressDto(stage[0], stage[1], etaPickup, etaDrop, vehicleAt, coRiders, seatsTaken, capacity, stops);
    }

    private static String[] stage(Booking booking, Trip trip) {
        return switch (booking.getStatus()) {
            case SCHEDULED -> new String[]{"SCHEDULED", "Scheduled"};
            case PENDING_APPROVAL -> new String[]{"AWAITING_APPROVAL", "Waiting for approval"};
            case REQUESTED -> new String[]{"FINDING_CAB", "Finding your cab"};
            case ASSIGNED -> trip != null && trip.getCurrentSeq() != null && trip.getCurrentSeq() == booking.getFromSeq()
                    ? new String[]{"AT_PICKUP", "Your cab has arrived"}
                    : new String[]{"ON_THE_WAY", "Cab on the way"};
            case ONBOARD -> new String[]{"ON_TRIP", "Trip in progress"};
            case COMPLETED -> new String[]{"COMPLETED", "Trip complete"};
            case CANCELLED -> new String[]{"CLOSED", "Cancelled"};
            case NO_SHOW -> new String[]{"CLOSED", "No-show"};
            case REJECTED -> new String[]{"CLOSED", "Not approved"};
        };
    }
}
