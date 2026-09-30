package com.srmecotech.plantride.matching;

import com.srmecotech.plantride.booking.Booking;
import com.srmecotech.plantride.booking.BookingRepository;
import com.srmecotech.plantride.booking.BookingStatus;
import com.srmecotech.plantride.booking.RideType;
import com.srmecotech.plantride.common.config.AppProperties;
import com.srmecotech.plantride.masterdata.ComplianceService;
import com.srmecotech.plantride.masterdata.Driver;
import com.srmecotech.plantride.masterdata.Route;
import com.srmecotech.plantride.masterdata.RoutePlan;
import com.srmecotech.plantride.masterdata.Vehicle;
import com.srmecotech.plantride.masterdata.VehicleStatus;
import com.srmecotech.plantride.trip.Duty;
import com.srmecotech.plantride.trip.DutyRepository;
import com.srmecotech.plantride.trip.Trip;
import com.srmecotech.plantride.trip.TripRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Phase-1 matching: filter on hard rules, then score what is left
 * (deck slide 14). Pure evaluation: it changes nothing, so the same call
 * powers the rider's ride options, dispatch, and the desk's "why not this
 * cab?" view.
 *
 * <p>Hard rules, in order: shuttle (timetabled, never matched), off-road,
 * expired vehicle or driver documents, busy on another route or an
 * exclusive ride, already past the pickup, occupancy cap on the rider's
 * stretch, detour limit, driver duty hours.
 */
@Service
public class MatchingEngine {

    private final DutyRepository dutyRepository;
    private final TripRepository tripRepository;
    private final BookingRepository bookingRepository;
    private final ComplianceService complianceService;
    private final EtaCalculator etaCalculator;
    private final AppProperties.Matching weights;
    private final Clock clock;

    public MatchingEngine(DutyRepository dutyRepository, TripRepository tripRepository,
                          BookingRepository bookingRepository, ComplianceService complianceService,
                          EtaCalculator etaCalculator, AppProperties properties, Clock clock) {
        this.dutyRepository = dutyRepository;
        this.tripRepository = tripRepository;
        this.bookingRepository = bookingRepository;
        this.complianceService = complianceService;
        this.etaCalculator = etaCalculator;
        this.weights = properties.matching();
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MatchOutcome evaluate(MatchRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        Route route = request.plan().route();

        Map<Long, Trip> openTripByVehicle = new HashMap<>();
        for (Trip t : tripRepository.findOpenTrips()) {
            openTripByVehicle.put(t.getVehicle().getId(), t);
        }
        List<Long> tripsOnRoute = openTripByVehicle.values().stream()
                .filter(t -> t.getRoute().getId().equals(route.getId()))
                .map(Trip::getId)
                .toList();
        Map<Long, List<Booking>> ridersByTrip = tripsOnRoute.isEmpty() ? Map.of()
                : bookingRepository.findOnTrips(tripsOnRoute, BookingStatus.ON_TRIP).stream()
                .filter(b -> !b.getId().equals(request.ignoreBookingId()))
                .collect(Collectors.groupingBy(b -> b.getTrip().getId()));

        List<Candidate> feasible = new ArrayList<>();
        List<Rejection> rejected = new ArrayList<>();
        for (Duty duty : dutyRepository.findActiveWithVehicleAndDriver()) {
            Vehicle vehicle = duty.getVehicle();
            if (request.onlyVehicleId() != null && !request.onlyVehicleId().equals(vehicle.getId())) {
                continue;
            }
            Trip trip = openTripByVehicle.get(vehicle.getId());
            List<Booking> riders = trip == null ? List.of() : ridersByTrip.getOrDefault(trip.getId(), List.of());
            Evaluation evaluation = evaluateOne(request, duty, trip, riders, now);
            if (evaluation.candidate() != null) {
                feasible.add(evaluation.candidate());
            } else {
                rejected.add(new Rejection(vehicle.getId(), vehicle.getCode(), evaluation.rejection()));
            }
        }
        feasible.sort(Comparator.comparingDouble(Candidate::score)
                .thenComparing(c -> c.vehicle().getCode()));
        return new MatchOutcome(feasible, rejected);
    }

    private Evaluation evaluateOne(MatchRequest request, Duty duty, Trip trip, List<Booking> riders, LocalDateTime now) {
        RoutePlan plan = request.plan();
        Route route = plan.route();
        Vehicle vehicle = duty.getVehicle();
        Driver driver = duty.getDriver();
        String pickupName = plan.stopAt(request.fromSeq()).getName();
        String dropName = plan.stopAt(request.toSeq()).getName();

        if (vehicle.isShuttle()) {
            return Evaluation.reject("runs the shuttle timetable and is not dispatched");
        }
        if (vehicle.getStatus() == VehicleStatus.OFF_ROAD) {
            return Evaluation.reject("is off-road");
        }
        Optional<String> vehicleBlock = complianceService.vehicleBlockReason(vehicle);
        if (vehicleBlock.isPresent()) {
            return Evaluation.reject("is blocked: " + vehicleBlock.get());
        }
        Optional<String> driverBlock = complianceService.driverBlockReason(driver);
        if (driverBlock.isPresent()) {
            return Evaluation.reject("is blocked: " + driverBlock.get());
        }

        int eta;
        int seatsTaken;
        int capacity;
        double emptyKm;
        int detour;
        if (trip != null) {
            if (request.rideType() == RideType.EXCLUSIVE) {
                return Evaluation.reject("is already on a trip (an exclusive ride needs an empty cab)");
            }
            if (trip.isExclusiveRide()) {
                return Evaluation.reject("is on an exclusive ride");
            }
            if (!trip.getRoute().getId().equals(route.getId())) {
                return Evaluation.reject("is on " + trip.getRoute().label());
            }
            int position = trip.getCurrentSeq() != null ? trip.getCurrentSeq() : trip.getStartSeq();
            if (request.fromSeq() < position) {
                return Evaluation.reject("has already passed " + pickupName);
            }
            capacity = Math.min(route.getMaxPassengers(), vehicle.getSeatCapacity());
            seatsTaken = SeatLoad.maxLoad(riders, request.fromSeq(), request.toSeq());
            if (seatsTaken + request.seats() > capacity) {
                return Evaluation.reject("is at its " + capacity + "-rider cap (" + seatsTaken + " of " + capacity
                        + " between " + pickupName + " and " + dropName + ")");
            }
            Set<Integer> stopping = new HashSet<>();
            riders.forEach(b -> {
                stopping.add(b.getFromSeq());
                stopping.add(b.getToSeq());
            });
            int newStops = (stopping.contains(request.fromSeq()) ? 0 : 1) + (stopping.contains(request.toSeq()) ? 0 : 1);
            detour = riders.isEmpty() ? 0 : newStops * weights.dwellMinutes();
            if (detour > route.getMaxDetourMin()) {
                return Evaluation.reject("would add " + detour + " min for riders on board (limit "
                        + route.getMaxDetourMin() + " min)");
            }
            eta = etaCalculator.minutesUntil(trip, plan, request.fromSeq(), stopping);
            emptyKm = 0;
        } else {
            capacity = request.rideType() == RideType.EXCLUSIVE
                    ? vehicle.getSeatCapacity()
                    : Math.min(route.getMaxPassengers(), vehicle.getSeatCapacity());
            if (request.seats() > capacity) {
                return Evaluation.reject("has " + capacity + " seats for this ride; the group needs " + request.seats());
            }
            seatsTaken = 0;
            detour = 0;
            EtaCalculator.Reposition reposition = etaCalculator.reposition(vehicle, plan, request.fromSeq());
            eta = reposition.minutes();
            emptyKm = reposition.km();
        }

        long onDuty = duty.minutesOnDuty(now);
        int needed = eta + plan.minutesBetween(request.fromSeq(), request.toSeq());
        if (onDuty + needed > driver.getMaxDutyMinutes()) {
            return Evaluation.reject("driver " + driver.getUser().getFullName() + " would pass the duty-hour limit ("
                    + hours(onDuty) + " of " + hours(driver.getMaxDutyMinutes()) + " used)");
        }

        double score = weights.weightEta() * eta
                + weights.weightDetour() * detour
                + weights.weightEmptyKm() * emptyKm
                - weights.weightPooling() * seatsTaken
                + (vehicle.isOwnFleet() ? 0 : weights.vendorPenalty());
        String reason = String.format(Locale.ROOT, "%s · ETA %d min · %d of %d seats taken · %.1f empty km · %s · score %.2f",
                vehicle.getCode(), eta, seatsTaken, capacity, emptyKm,
                vehicle.isOwnFleet() ? "own fleet" : "vendor", score);
        return Evaluation.accept(new Candidate(vehicle, driver, duty, trip, eta, seatsTaken, capacity, emptyKm,
                detour, score, reason));
    }

    private static String hours(long minutes) {
        return (minutes / 60) + "h" + String.format("%02d", minutes % 60) + "m";
    }

    private record Evaluation(Candidate candidate, String rejection) {
        static Evaluation accept(Candidate candidate) {
            return new Evaluation(candidate, null);
        }

        static Evaluation reject(String reason) {
            return new Evaluation(null, reason);
        }
    }
}
