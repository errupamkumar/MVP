package com.srmecotech.plantride.booking;

import com.srmecotech.plantride.booking.dto.RiderDtos.AlternativeSpotDto;
import com.srmecotech.plantride.booking.dto.RiderDtos.AvailabilityDto;
import com.srmecotech.plantride.booking.dto.RiderDtos.NearbyCabDto;
import com.srmecotech.plantride.booking.dto.RiderDtos.NextRideDto;
import com.srmecotech.plantride.booking.dto.RiderDtos.SearchStatusDto;
import com.srmecotech.plantride.booking.dto.RiderDtos.ShuttleInfoDto;
import com.srmecotech.plantride.booking.dto.RiderDtos.SpotDto;
import com.srmecotech.plantride.common.config.AppProperties;
import com.srmecotech.plantride.common.settings.SettingsService;
import com.srmecotech.plantride.common.util.GeoMath;
import com.srmecotech.plantride.masterdata.ComplianceService;
import com.srmecotech.plantride.masterdata.Driver;
import com.srmecotech.plantride.masterdata.ResolvedRide;
import com.srmecotech.plantride.masterdata.RoutePlan;
import com.srmecotech.plantride.masterdata.RouteResolver;
import com.srmecotech.plantride.masterdata.Stop;
import com.srmecotech.plantride.masterdata.StopRepository;
import com.srmecotech.plantride.masterdata.Vehicle;
import com.srmecotech.plantride.masterdata.VehicleStatus;
import com.srmecotech.plantride.masterdata.dto.MasterDataDtos.RouteRef;
import com.srmecotech.plantride.matching.Candidate;
import com.srmecotech.plantride.matching.DispatchService;
import com.srmecotech.plantride.matching.EtaCalculator;
import com.srmecotech.plantride.matching.MatchOutcome;
import com.srmecotech.plantride.matching.MatchRequest;
import com.srmecotech.plantride.matching.MatchingEngine;
import com.srmecotech.plantride.matching.Rejection;
import com.srmecotech.plantride.matching.SeatLoad;
import com.srmecotech.plantride.trip.Duty;
import com.srmecotech.plantride.trip.DutyRepository;
import com.srmecotech.plantride.trip.Trip;
import com.srmecotech.plantride.trip.TripRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Answers "when can a ride reach me?" for a pickup and drop: the next cab or
 * shuttle at the pickup (even when none is free right now), every vehicle on
 * duty with its driver, seats and distance, and admin-configured pickup points
 * within walking distance where a ride comes sooner.
 *
 * <p>Read-only: it runs the same matching engine as dispatch, so what the
 * rider is told matches what the engine will do.
 */
@Service
public class AvailabilityService {

    /** Walking pace across the plant, and how much longer the walk is than the straight line. */
    private static final double WALK_METRES_PER_MINUTE = 75d;
    private static final double WALK_ROUTE_FACTOR = 1.25d;
    private static final int MAX_UPCOMING = 3;
    private static final int MAX_ALTERNATIVES = 3;

    private final RouteResolver routeResolver;
    private final MatchingEngine matchingEngine;
    private final EtaCalculator etaCalculator;
    private final DutyRepository dutyRepository;
    private final TripRepository tripRepository;
    private final BookingRepository bookingRepository;
    private final StopRepository stopRepository;
    private final ComplianceService complianceService;
    private final ShuttleTimetable shuttleTimetable;
    private final DispatchService dispatchService;
    private final SettingsService settings;
    private final AppProperties properties;
    private final Clock clock;

    public AvailabilityService(RouteResolver routeResolver, MatchingEngine matchingEngine, EtaCalculator etaCalculator,
                               DutyRepository dutyRepository, TripRepository tripRepository,
                               BookingRepository bookingRepository, StopRepository stopRepository,
                               ComplianceService complianceService, ShuttleTimetable shuttleTimetable,
                               DispatchService dispatchService, SettingsService settings, AppProperties properties,
                               Clock clock) {
        this.routeResolver = routeResolver;
        this.matchingEngine = matchingEngine;
        this.etaCalculator = etaCalculator;
        this.dutyRepository = dutyRepository;
        this.tripRepository = tripRepository;
        this.bookingRepository = bookingRepository;
        this.stopRepository = stopRepository;
        this.complianceService = complianceService;
        this.shuttleTimetable = shuttleTimetable;
        this.dispatchService = dispatchService;
        this.settings = settings;
        this.properties = properties;
        this.clock = clock;
    }

    /** Availability for a ride the rider is still choosing. */
    @Transactional(readOnly = true)
    public AvailabilityDto forRide(Long fromStopId, Long toStopId, int seats) {
        return build(routeResolver.resolve(fromStopId, toStopId), seats, null);
    }

    /** Availability for a booking that is waiting for a cab, with how the search is going. */
    @Transactional(readOnly = true)
    public AvailabilityDto forBooking(Booking booking) {
        RoutePlan plan = routeResolver.planFor(booking.getRoute().getId());
        ResolvedRide ride = new ResolvedRide(plan, booking.getFromSeq(), booking.getToSeq());
        return build(ride, booking.getSeats(), booking);
    }

    // ------------------------------------------------------------------ assembly

    private AvailabilityDto build(ResolvedRide ride, int seats, Booking booking) {
        LocalDateTime now = LocalDateTime.now(clock);
        Fleet fleet = Fleet.load(dutyRepository, tripRepository, bookingRepository);
        Long ignore = booking == null ? null : booking.getId();

        Timing atPickup = timing(ride, seats, ignore, fleet, now);
        List<NextRideDto> upcoming = atPickup.upcoming().stream().limit(MAX_UPCOMING).toList();
        NextRideDto next = upcoming.isEmpty() ? null : upcoming.get(0);
        boolean availableNow = next != null && next.availableNow() && "CAB".equals(next.kind());

        List<AlternativeSpotDto> alternatives = alternatives(ride, seats, ignore, fleet, now, next);
        List<NearbyCabDto> nearby = nearbyCabs(ride, atPickup, fleet, now);

        return new AvailabilityDto(now, RouteRef.of(ride.route()), spot(ride.from()), spot(ride.to()),
                fleet.duties().size(), availableNow, summary(ride.from(), next, alternatives),
                next, upcoming, nearby, alternatives, booking == null ? null : search(booking, now));
    }

    /**
     * Everything that can reach one pickup for one ride: cabs the engine
     * would assign now, busy cabs once their run ends, and the shuttle.
     */
    private Timing timing(ResolvedRide ride, int seats, Long ignoreBookingId, Fleet fleet, LocalDateTime now) {
        RoutePlan plan = ride.plan();
        Stop pickup = ride.from();
        MatchOutcome outcome = matchingEngine.evaluate(new MatchRequest(plan, ride.fromSeq(), ride.toSeq(), seats,
                RideType.SHARED, null, ignoreBookingId));
        Map<Long, Candidate> feasible = outcome.feasible().stream()
                .collect(Collectors.toMap(c -> c.vehicle().getId(), Function.identity(), (a, b) -> a));
        Map<Long, String> rejected = outcome.rejected().stream()
                .collect(Collectors.toMap(Rejection::vehicleId, Rejection::reason, (a, b) -> a));

        List<NextRideDto> upcoming = new ArrayList<>();
        Map<Long, Estimate> estimates = new HashMap<>();
        for (Duty duty : fleet.duties()) {
            Vehicle vehicle = duty.getVehicle();
            if (vehicle.isShuttle()) {
                continue;
            }
            Double distance = etaCalculator.roadKmTo(vehicle, pickup);
            Candidate candidate = feasible.get(vehicle.getId());
            if (candidate != null) {
                Estimate e = new Estimate(true, candidate.etaMinutes(), candidate.trip() == null
                        ? "Free now" : "Joins its run with " + candidate.seatsTaken() + " of " + candidate.capacity() + " seats taken");
                estimates.put(vehicle.getId(), e);
                upcoming.add(cabRide(duty, e, distance, now));
                continue;
            }
            Estimate e = freeUpEstimate(duty, fleet, plan, ride, now);
            estimates.put(vehicle.getId(), e == null ? new Estimate(false, null, rejected.get(vehicle.getId())) : e);
            if (e != null) {
                upcoming.add(cabRide(duty, e, distance, now));
            }
        }

        Optional<ShuttleInfoDto> shuttle = shuttleTimetable.forStop(plan, ride.fromSeq(), now);
        shuttle.ifPresent(s -> upcoming.add(new NextRideDto("SHUTTLE", s.vehicleCode(), null, null, s.minutesToNext(),
                now.plusMinutes(s.minutesToNext()), null, false,
                s.firstRunAt() != null
                        ? "First run " + s.firstRunAt() + ", then every " + s.frequencyMin() + " min · walk up, no booking"
                        : s.routeLabel() + " loop every " + s.frequencyMin() + " min · walk up, no booking")));

        upcoming.sort(Comparator.comparingInt(NextRideDto::inMinutes)
                .thenComparing(r -> !r.availableNow())
                .thenComparing(NextRideDto::vehicleCode));
        return new Timing(upcoming, feasible, rejected, estimates);
    }

    /**
     * When a cab that cannot take the rider now could be at the pickup: after
     * the last drop of its current run, plus the drive from there. Null when it
     * will not come at all (off-road, blocked, or the shift ends first).
     */
    private Estimate freeUpEstimate(Duty duty, Fleet fleet, RoutePlan plan, ResolvedRide ride, LocalDateTime now) {
        Vehicle vehicle = duty.getVehicle();
        Driver driver = duty.getDriver();
        if (vehicle.getStatus() == VehicleStatus.OFF_ROAD
                || complianceService.vehicleBlockReason(vehicle).isPresent()
                || complianceService.driverBlockReason(driver).isPresent()) {
            return null;
        }
        Trip trip = fleet.openTripByVehicle().get(vehicle.getId());
        if (trip == null) {
            return null; // idle but ruled out (duty hours, group size): it will not become eligible by waiting
        }
        RoutePlan tripPlan = fleet.plan(trip, routeResolver);
        List<Booking> riders = fleet.ridersByTrip().getOrDefault(trip.getId(), List.of());
        int position = trip.getCurrentSeq() != null ? trip.getCurrentSeq() : trip.getStartSeq();
        int lastDrop = riders.stream().mapToInt(Booking::getToSeq).max().orElse(position);
        Set<Integer> stopping = new HashSet<>();
        riders.forEach(b -> {
            stopping.add(b.getFromSeq());
            stopping.add(b.getToSeq());
        });
        int finish = etaCalculator.minutesUntil(trip, tripPlan, lastDrop, stopping);
        Stop endStop = tripPlan.stopAt(lastDrop);
        int drive = etaCalculator.fromStop(endStop, plan, ride.fromSeq()).minutes();
        int total = finish + drive;
        long needed = duty.minutesOnDuty(now) + total + ride.minutes();
        if (needed > driver.getMaxDutyMinutes()) {
            return null;
        }
        String note = riders.isEmpty()
                ? "After finishing its current run"
                : "After its last drop at " + endStop.getName() + (drive > 0 ? ", then " + drive + " min to " + ride.from().getName() : "");
        return new Estimate(false, Math.max(1, total), note);
    }

    private List<NearbyCabDto> nearbyCabs(ResolvedRide ride, Timing timing, Fleet fleet, LocalDateTime now) {
        Stop pickup = ride.from();
        List<NearbyCabDto> cabs = new ArrayList<>();
        for (Duty duty : fleet.duties()) {
            Vehicle vehicle = duty.getVehicle();
            Driver driver = duty.getDriver();
            Trip trip = fleet.openTripByVehicle().get(vehicle.getId());
            List<Booking> riders = trip == null ? List.of() : fleet.ridersByTrip().getOrDefault(trip.getId(), List.of());
            Double distance = etaCalculator.roadKmTo(vehicle, pickup);
            String fleetName = vehicle.isOwnFleet() ? "LHS own fleet" : vehicle.getVendor().getName();
            String at = trip != null && trip.getCurrentSeq() != null
                    ? fleet.plan(trip, routeResolver).stopAt(trip.getCurrentSeq()).getName()
                    : vehicle.getCurrentStop() != null ? vehicle.getCurrentStop().getName() : null;

            if (vehicle.isShuttle()) {
                Optional<ShuttleInfoDto> s = shuttleTimetable.forStop(ride.plan(), ride.fromSeq(), now)
                        .filter(info -> info.vehicleCode().equals(vehicle.getCode()));
                cabs.add(new NearbyCabDto(vehicle.getId(), vehicle.getCode(), vehicle.getRegistrationNo(),
                        vehicle.getVehicleType().name(), fleetName, driver.getUser().getFullName(), driver.getUser().getPhone(),
                        "SHUTTLE", "Shuttle", at, distance, 0, vehicle.getSeatCapacity(), false, null,
                        s.map(ShuttleInfoDto::minutesToNext).orElse(null),
                        s.map(info -> "Timetabled · at " + pickup.getName() + " in " + info.minutesToNext() + " min · walk up")
                                .orElse("Timetabled on " + (vehicle.getHomeRoute() == null ? "its route" : vehicle.getHomeRoute().label())
                                        + " · does not serve this ride")));
                continue;
            }

            Candidate candidate = timing.feasible().get(vehicle.getId());
            Estimate estimate = timing.estimates().get(vehicle.getId());
            int capacity = candidate != null ? candidate.capacity()
                    : Math.min(ride.route().getMaxPassengers(), vehicle.getSeatCapacity());
            int seatsTaken = candidate != null ? candidate.seatsTaken()
                    : trip == null ? 0 : SeatLoad.maxLoad(riders, 0, fleet.plan(trip, routeResolver).lastSeq());
            String[] status = status(vehicle, driver, trip, candidate, riders, capacity, fleet);
            cabs.add(new NearbyCabDto(vehicle.getId(), vehicle.getCode(), vehicle.getRegistrationNo(),
                    vehicle.getVehicleType().name(), fleetName, driver.getUser().getFullName(), driver.getUser().getPhone(),
                    status[0], status[1], at, distance, seatsTaken, capacity, candidate != null,
                    candidate != null ? candidate.etaMinutes() : null,
                    candidate == null && estimate != null ? estimate.minutes() : null,
                    candidate != null ? estimate.note()
                            : estimate != null && estimate.minutes() != null ? estimate.note()
                            : capitalise(timing.rejected().getOrDefault(vehicle.getId(), "is not available"))));
        }
        cabs.sort(Comparator.comparing((NearbyCabDto c) -> c.distanceKm() == null ? Double.MAX_VALUE : c.distanceKm())
                .thenComparing(NearbyCabDto::vehicleCode));
        return cabs;
    }

    private String[] status(Vehicle vehicle, Driver driver, Trip trip, Candidate candidate, List<Booking> riders,
                            int capacity, Fleet fleet) {
        if (vehicle.getStatus() == VehicleStatus.OFF_ROAD) {
            return new String[]{"OFF_ROAD", "Off-road"};
        }
        if (complianceService.vehicleBlockReason(vehicle).isPresent() || complianceService.driverBlockReason(driver).isPresent()) {
            return new String[]{"BLOCKED", "Blocked"};
        }
        if (candidate != null) {
            return trip == null ? new String[]{"FREE", "Free"} : new String[]{"ON_TRIP", "Can join its run"};
        }
        if (trip == null) {
            return new String[]{"UNAVAILABLE", "Not available"};
        }
        if (trip.isExclusiveRide()) {
            return new String[]{"ON_TRIP", "On an exclusive ride"};
        }
        int onBoard = riders.stream().mapToInt(Booking::getSeats).sum();
        if (onBoard >= capacity) {
            return new String[]{"FULL", "Full · " + trip.getRoute().label()};
        }
        return new String[]{"ON_TRIP", "On trip · " + trip.getRoute().label()};
    }

    /**
     * Pickup points within walking distance of the rider's pickup, on a route
     * to the same drop, where a ride can come. Marked "sooner" when walking
     * there and catching it beats waiting at the chosen pickup.
     */
    private List<AlternativeSpotDto> alternatives(ResolvedRide ride, int seats, Long ignoreBookingId, Fleet fleet,
                                                  LocalDateTime now, NextRideDto nextAtPickup) {
        Stop pickup = ride.from();
        Stop drop = ride.to();
        int radius = settings.getInt(SettingsService.NEARBY_SPOT_RADIUS_M, 1200);
        List<AlternativeSpotDto> found = new ArrayList<>();
        for (Stop stop : stopRepository.findByActiveTrueOrderByNameAsc()) {
            if (stop.getId().equals(pickup.getId()) || stop.getId().equals(drop.getId())) {
                continue;
            }
            double metres = GeoMath.distanceMetres(pickup.getLatitude(), pickup.getLongitude(), stop.getLatitude(), stop.getLongitude());
            if (metres > radius) {
                continue;
            }
            Optional<ResolvedRide> resolved = routeResolver.tryResolve(stop.getId(), drop.getId());
            if (resolved.isEmpty()) {
                continue;
            }
            ResolvedRide alt = resolved.get();
            Optional<NextRideDto> first = timing(alt, seats, ignoreBookingId, fleet, now).upcoming().stream()
                    .filter(r -> "CAB".equals(r.kind()))
                    .findFirst();
            if (first.isEmpty()) {
                continue;
            }
            NextRideDto r = first.get();
            int walkMetres = (int) Math.round(metres * WALK_ROUTE_FACTOR);
            int walkMinutes = (int) Math.ceil(walkMetres / WALK_METRES_PER_MINUTE);
            int ready = Math.max(walkMinutes, r.inMinutes());
            boolean sooner = nextAtPickup == null || ready < nextAtPickup.inMinutes();
            String note = (r.availableNow() ? r.vehicleCode() + " can be there in " + r.inMinutes() + " min"
                    : r.vehicleCode() + " can be there in about " + r.inMinutes() + " min")
                    + " · " + walkMinutes + " min walk";
            found.add(new AlternativeSpotDto(spot(stop), RouteRef.of(alt.route()), walkMetres, walkMinutes, r.inMinutes(),
                    ready, r.vehicleCode(), r.availableNow(), sooner, note));
        }
        found.sort(Comparator.comparing((AlternativeSpotDto a) -> !a.sooner())
                .thenComparingInt(AlternativeSpotDto::readyInMinutes)
                .thenComparingInt(AlternativeSpotDto::walkMetres));
        return found.stream().limit(MAX_ALTERNATIVES).toList();
    }

    private SearchStatusDto search(Booking booking, LocalDateTime now) {
        long every = Math.max(1, properties.dispatch().intervalMs() / 1000);
        LocalDateTime lastSweep = dispatchService.lastSweepAt();
        long sinceSweep = lastSweep == null ? 0 : Math.max(0, Duration.between(lastSweep, now).toSeconds());
        long nextCheck = lastSweep == null ? every : Math.max(0, every - (sinceSweep % every));
        int expiryMinutes = settings.getInt(SettingsService.REQUEST_EXPIRY_MINUTES, 90);
        LocalDateTime expiresAt = booking.getCreatedAt().plusMinutes(expiryMinutes);
        Long lastChecked = booking.getUpdatedAt() == null ? null
                : Math.max(0, Duration.between(booking.getUpdatedAt(), now).toSeconds());
        return new SearchStatusDto(booking.getId(), booking.getBookingCode(),
                Math.max(0, Duration.between(booking.getCreatedAt(), now).toSeconds()),
                lastChecked, nextCheck, every, Math.max(0, Duration.between(now, expiresAt).toSeconds()), expiresAt,
                booking.getMatchNote());
    }

    private static String summary(Stop pickup, NextRideDto next, List<AlternativeSpotDto> alternatives) {
        Optional<AlternativeSpotDto> better = alternatives.stream().filter(AlternativeSpotDto::sooner).findFirst();
        String walk = better.map(a -> " Or walk " + a.walkMinutes() + " min to " + a.stop().name() + ": "
                + a.vehicleCode() + " can be there in " + a.rideInMinutes() + " min.").orElse("");
        if (next == null) {
            return "No cab on duty can reach " + pickup.getName() + " right now. Your request stays in the queue"
                    + " and the transport desk can see it." + walk;
        }
        if (next.availableNow() && "CAB".equals(next.kind())) {
            return next.vehicleCode() + " can pick you up at " + pickup.getName() + " in "
                    + (next.inMinutes() < 1 ? "under a minute" : next.inMinutes() + " min") + ".";
        }
        if ("SHUTTLE".equals(next.kind())) {
            return "No cab is free right now. Shuttle " + next.vehicleCode() + " reaches " + pickup.getName() + " in "
                    + next.inMinutes() + " min (walk up, no booking)." + walk;
        }
        return "No cab is free right now. The earliest is " + next.vehicleCode() + " at " + pickup.getName()
                + " in about " + next.inMinutes() + " min (" + decapitalise(next.note()) + ")." + walk;
    }

    private NextRideDto cabRide(Duty duty, Estimate e, Double distance, LocalDateTime now) {
        Vehicle v = duty.getVehicle();
        return new NextRideDto("CAB", v.getCode(), v.getRegistrationNo(), duty.getDriver().getUser().getFullName(),
                e.minutes(), now.plusMinutes(e.minutes()), distance, e.availableNow(), e.note());
    }

    private static SpotDto spot(Stop s) {
        return new SpotDto(s.getId(), s.getCode(), s.getName(), s.getZone(), s.getLandmark());
    }

    private static String decapitalise(String text) {
        return text == null || text.isEmpty() ? "" : Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }

    private static String capitalise(String reason) {
        return reason.isEmpty() ? reason : Character.toUpperCase(reason.charAt(0)) + reason.substring(1);
    }

    // ------------------------------------------------------------------ working types

    private record Estimate(boolean availableNow, Integer minutes, String note) {
    }

    private record Timing(List<NextRideDto> upcoming, Map<Long, Candidate> feasible, Map<Long, String> rejected,
                          Map<Long, Estimate> estimates) {
    }

    /** One read of the fleet (duties, open trips, riders on them) shared by every calculation in a request. */
    private record Fleet(List<Duty> duties, Map<Long, Trip> openTripByVehicle, Map<Long, List<Booking>> ridersByTrip,
                         Map<Long, RoutePlan> plans) {

        static Fleet load(DutyRepository dutyRepository, TripRepository tripRepository, BookingRepository bookingRepository) {
            List<Duty> duties = dutyRepository.findActiveWithVehicleAndDriver();
            Map<Long, Trip> trips = new HashMap<>();
            for (Trip t : tripRepository.findOpenTrips()) {
                trips.put(t.getVehicle().getId(), t);
            }
            List<Long> tripIds = trips.values().stream().map(Trip::getId).toList();
            Map<Long, List<Booking>> riders = tripIds.isEmpty() ? Map.of()
                    : bookingRepository.findOnTrips(tripIds, BookingStatus.ON_TRIP).stream()
                    .collect(Collectors.groupingBy(b -> b.getTrip().getId()));
            return new Fleet(duties, trips, riders, new HashMap<>());
        }

        RoutePlan plan(Trip trip, RouteResolver resolver) {
            return plans.computeIfAbsent(trip.getRoute().getId(), resolver::planFor);
        }
    }
}
