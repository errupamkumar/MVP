package com.srmecotech.plantride.trip;

import com.srmecotech.plantride.booking.Booking;
import com.srmecotech.plantride.booking.BookingRepository;
import com.srmecotech.plantride.booking.BookingStatus;
import com.srmecotech.plantride.common.settings.SettingsService;
import com.srmecotech.plantride.masterdata.RoutePlan;
import com.srmecotech.plantride.masterdata.RouteResolver;
import com.srmecotech.plantride.masterdata.dto.MasterDataDtos.RouteRef;
import com.srmecotech.plantride.matching.EtaCalculator;
import com.srmecotech.plantride.trip.dto.DriverDtos.QueueRiderDto;
import com.srmecotech.plantride.trip.dto.DriverDtos.QueueStopDto;
import com.srmecotech.plantride.trip.dto.DriverDtos.TripQueueDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** Builds the driver's sequenced queue: stops in route order with who boards and who leaves at each. */
@Service
public class DriverQueueService {

    private static final Set<BookingStatus> SHOWN = EnumSet.of(
            BookingStatus.ASSIGNED, BookingStatus.ONBOARD, BookingStatus.COMPLETED, BookingStatus.NO_SHOW);

    private final BookingRepository bookingRepository;
    private final RouteResolver routeResolver;
    private final EtaCalculator etaCalculator;
    private final SettingsService settings;
    private final Clock clock;

    public DriverQueueService(BookingRepository bookingRepository, RouteResolver routeResolver,
                              EtaCalculator etaCalculator, SettingsService settings, Clock clock) {
        this.bookingRepository = bookingRepository;
        this.routeResolver = routeResolver;
        this.etaCalculator = etaCalculator;
        this.settings = settings;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TripQueueDto build(Trip trip) {
        LocalDateTime now = LocalDateTime.now(clock);
        RoutePlan plan = routeResolver.planFor(trip.getRoute().getId());
        List<Booking> riders = bookingRepository.findOnTrip(trip.getId(), SHOWN);
        List<Booking> active = riders.stream().filter(b -> BookingStatus.ON_TRIP.contains(b.getStatus())).toList();
        int otpMax = settings.getInt(SettingsService.OTP_MAX_ATTEMPTS, 5);

        Set<Integer> itinerary = new TreeSet<>();
        Set<Integer> activeStops = new TreeSet<>();
        for (Booking b : riders) {
            itinerary.add(b.getFromSeq());
            itinerary.add(b.getToSeq());
        }
        for (Booking b : active) {
            activeStops.add(b.getFromSeq());
            activeStops.add(b.getToSeq());
        }

        Integer current = trip.getCurrentSeq();
        Integer next = TripItinerary.nextStoppingSeq(trip, active);
        List<QueueStopDto> stops = new ArrayList<>();
        for (int seq : itinerary) {
            String state;
            if (current != null && seq < current) {
                state = "DONE";
            } else if (current != null && seq == current) {
                state = "CURRENT";
            } else if (next != null && seq == next) {
                state = "NEXT";
            } else if (current == null && seq < trip.getStartSeq()) {
                state = "DONE";
            } else {
                state = "UPCOMING";
            }
            int eta = "DONE".equals(state) || "CURRENT".equals(state) ? 0
                    : etaCalculator.minutesUntil(trip, plan, seq, activeStops);
            List<QueueRiderDto> pickups = riders.stream().filter(b -> b.getFromSeq() == seq)
                    .map(b -> rider(b, trip, plan, now, otpMax)).toList();
            List<QueueRiderDto> drops = riders.stream().filter(b -> b.getToSeq() == seq)
                    .map(b -> rider(b, trip, plan, now, otpMax)).toList();
            stops.add(new QueueStopDto(seq, plan.stopAt(seq).getId(), plan.stopAt(seq).getName(), state, eta,
                    summary(pickups, drops), pickups, drops));
        }

        int capacity = trip.isExclusiveRide()
                ? trip.getVehicle().getSeatCapacity()
                : Math.min(trip.getRoute().getMaxPassengers(), trip.getVehicle().getSeatCapacity());
        int onBoard = active.stream().filter(b -> b.getStatus() == BookingStatus.ONBOARD).mapToInt(Booking::getSeats).sum();
        Optional<String> blocker = TripItinerary.arrivalBlocker(trip, plan, active);
        QueueStopDto nextStop = next == null ? null
                : stops.stream().filter(s -> s.seq() == next).findFirst().orElse(null);

        return new TripQueueDto(trip.getId(), trip.getTripCode(), trip.getStatus(), RouteRef.of(trip.getRoute()),
                trip.isExclusiveRide(), capacity, onBoard, Math.max(0, capacity - onBoard), current,
                current == null ? null : plan.stopAt(current).getName(), nextStop, blocker.isEmpty(),
                blocker.orElse(null), trip.getRoute().getSpeedLimitKmh(), stops);
    }

    private QueueRiderDto rider(Booking b, Trip trip, RoutePlan plan, LocalDateTime now, int otpMax) {
        Long noShowIn = null;
        if (b.getStatus() == BookingStatus.ASSIGNED && trip.getCurrentSeq() != null
                && trip.getCurrentSeq() == b.getFromSeq() && trip.getLastArrivedAt() != null) {
            long waited = Duration.between(trip.getLastArrivedAt(), now).getSeconds();
            noShowIn = Math.max(0, trip.getRoute().getMaxWaitMin() * 60L - waited);
        }
        String personnelNo = b.isForGuest() ? "Guest of " + b.getEmployee().getPersonnelNo() : b.getEmployee().getPersonnelNo();
        return new QueueRiderDto(b.getId(), b.getBookingCode(), b.getRiderName(), personnelNo, b.getRiderPhone(),
                b.getSeats(), b.getStatus().name(), plan.stopAt(b.getFromSeq()).getName(),
                plan.stopAt(b.getToSeq()).getName(), noShowIn, b.getOtpAttempts() >= otpMax);
    }

    private static String summary(List<QueueRiderDto> pickups, List<QueueRiderDto> drops) {
        int up = pickups.stream().filter(r -> !"NO_SHOW".equals(r.status())).mapToInt(QueueRiderDto::seats).sum();
        int down = drops.stream().filter(r -> !"NO_SHOW".equals(r.status())).mapToInt(QueueRiderDto::seats).sum();
        List<String> parts = new ArrayList<>();
        if (down > 0) {
            parts.add("Drop " + down + (pickups.isEmpty() ? (down == 1 ? " rider" : " riders") : ""));
        }
        if (up > 0) {
            parts.add((parts.isEmpty() ? "Pick up " : "pick up ") + up + (drops.isEmpty() ? (up == 1 ? " rider" : " riders") : ""));
        }
        return parts.isEmpty() ? "No action" : String.join(" · ", parts);
    }
}
