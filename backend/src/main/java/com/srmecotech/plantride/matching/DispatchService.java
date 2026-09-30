package com.srmecotech.plantride.matching;

import com.srmecotech.plantride.booking.Booking;
import com.srmecotech.plantride.booking.BookingRepository;
import com.srmecotech.plantride.booking.BookingStatus;
import com.srmecotech.plantride.booking.RideType;
import com.srmecotech.plantride.common.error.BusinessRuleException;
import com.srmecotech.plantride.common.error.NotFoundException;
import com.srmecotech.plantride.common.settings.SettingsService;
import com.srmecotech.plantride.common.util.Codes;
import com.srmecotech.plantride.masterdata.RouteRepository;
import com.srmecotech.plantride.masterdata.RouteResolver;
import com.srmecotech.plantride.masterdata.RoutePlan;
import com.srmecotech.plantride.masterdata.Stop;
import com.srmecotech.plantride.masterdata.Vehicle;
import com.srmecotech.plantride.masterdata.VehicleRepository;
import com.srmecotech.plantride.notification.NotificationService;
import com.srmecotech.plantride.safety.AlertService;
import com.srmecotech.plantride.safety.AlertSeverity;
import com.srmecotech.plantride.safety.AlertType;
import com.srmecotech.plantride.trip.Trip;
import com.srmecotech.plantride.trip.TripRepository;
import com.srmecotech.plantride.trip.TripStatus;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Turns a matching decision into an assignment. Concurrency rules:
 * <ul>
 *   <li>The booking's route row is locked (FOR UPDATE) before seat loads are
 *       read, so joins onto the same route are serialised and the cap holds.</li>
 *   <li>The chosen vehicle row is locked before a new trip is opened, and the
 *       "no open trip" check is repeated under that lock (another route may
 *       have just taken the cab). uk_trip_open_vehicle backs this up.</li>
 * </ul>
 */
@Service
public class DispatchService {

    private static final Logger log = LoggerFactory.getLogger(DispatchService.class);
    private static final int DEMAND_ALERT_WAIT_MINUTES = 8;

    private final RouteRepository routeRepository;
    private final RouteResolver routeResolver;
    private final VehicleRepository vehicleRepository;
    private final TripRepository tripRepository;
    private final BookingRepository bookingRepository;
    private final MatchingEngine engine;
    private final NotificationService notificationService;
    private final AlertService alertService;
    private final SettingsService settings;
    private final EntityManager entityManager;
    private final TransactionTemplate requiresNew;
    private final Clock clock;

    public DispatchService(RouteRepository routeRepository, RouteResolver routeResolver,
                           VehicleRepository vehicleRepository, TripRepository tripRepository,
                           BookingRepository bookingRepository, MatchingEngine engine,
                           NotificationService notificationService, AlertService alertService,
                           SettingsService settings, EntityManager entityManager,
                           PlatformTransactionManager transactionManager, Clock clock) {
        this.routeRepository = routeRepository;
        this.routeResolver = routeResolver;
        this.vehicleRepository = vehicleRepository;
        this.tripRepository = tripRepository;
        this.bookingRepository = bookingRepository;
        this.engine = engine;
        this.notificationService = notificationService;
        this.alertService = alertService;
        this.settings = settings;
        this.entityManager = entityManager;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    /**
     * Attaches a REQUESTED booking to the best feasible cab, or leaves it
     * REQUESTED with a note explaining why no cab qualified.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public DispatchResult dispatch(Booking booking) {
        if (booking.getStatus() != BookingStatus.REQUESTED) {
            return DispatchResult.unassigned("Booking is " + booking.getStatus());
        }
        lockRoute(booking);
        RoutePlan plan = routeResolver.planFor(booking.getRoute().getId());
        MatchOutcome outcome = engine.evaluate(MatchRequest.forBooking(plan, booking));
        for (Candidate candidate : outcome.feasible()) {
            if (attach(booking, candidate)) {
                return DispatchResult.assigned(candidate);
            }
        }
        booking.setMatchNote(noCabNote(outcome));
        booking.truncateMatchNote();
        booking.setUpdatedAt(LocalDateTime.now(clock));
        return DispatchResult.unassigned(booking.getMatchNote());
    }

    /**
     * Desk override: put the booking on one specific cab. Hard rules still
     * apply (the cap is never overridden); the reason is returned if refused.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Candidate dispatchTo(Booking booking, Long vehicleId) {
        if (booking.getStatus() != BookingStatus.REQUESTED) {
            throw new BusinessRuleException("NOT_ASSIGNABLE", "Only a waiting booking can be assigned; this one is "
                    + booking.getStatus().name().toLowerCase().replace('_', ' ') + ".");
        }
        lockRoute(booking);
        RoutePlan plan = routeResolver.planFor(booking.getRoute().getId());
        MatchOutcome outcome = engine.evaluate(MatchRequest.forBooking(plan, booking).onlyVehicle(vehicleId));
        if (outcome.feasible().isEmpty()) {
            String reason = outcome.rejected().isEmpty()
                    ? vehicleCode(vehicleId) + " is not on duty."
                    : outcome.rejected().get(0).describe() + ".";
            throw new BusinessRuleException("MATCH_REJECTED", reason);
        }
        Candidate candidate = outcome.feasible().get(0);
        if (!attach(booking, candidate)) {
            throw new BusinessRuleException("MATCH_REJECTED",
                    candidate.vehicle().getCode() + " was just given another trip. Pick another cab.");
        }
        return candidate;
    }

    /**
     * Promotes scheduled rides that are inside the dispatch lead time, then
     * tries every waiting booking, oldest first. Each booking runs in its own
     * transaction so one failure cannot roll back the others.
     *
     * @return how many bookings were assigned
     */
    public int dispatchPending() {
        LocalDateTime now = LocalDateTime.now(clock);
        int lead = settings.getInt(SettingsService.DISPATCH_LEAD_MINUTES, 20);
        int expiryMinutes = settings.getInt(SettingsService.REQUEST_EXPIRY_MINUTES, 90);
        requiresNew.executeWithoutResult(status -> {
            for (Booking due : bookingRepository.findScheduledDueBy(now.plusMinutes(lead))) {
                due.setStatus(BookingStatus.REQUESTED);
                due.setUpdatedAt(now);
            }
        });
        requiresNew.executeWithoutResult(status -> expireStaleRequests(now, expiryMinutes));

        List<Long> waiting = requiresNew.execute(status -> bookingRepository.findIdsByStatus(BookingStatus.REQUESTED));
        int assigned = 0;
        for (Long bookingId : waiting == null ? List.<Long>of() : waiting) {
            try {
                Boolean ok = requiresNew.execute(status -> {
                    Booking booking = bookingRepository.findById(bookingId).orElse(null);
                    if (booking == null) {
                        return false;
                    }
                    lockRoute(booking);
                    // Re-read under the route lock: another thread may have assigned it meanwhile.
                    entityManager.refresh(booking);
                    return booking.getStatus() == BookingStatus.REQUESTED && dispatch(booking).assigned();
                });
                if (Boolean.TRUE.equals(ok)) {
                    assigned++;
                }
            } catch (RuntimeException ex) {
                log.warn("Dispatch of booking {} failed and will be retried: {}", bookingId, ex.getMessage());
            }
        }
        raiseDemandAlerts(now);
        return assigned;
    }

    private boolean attach(Booking booking, Candidate candidate) {
        LocalDateTime now = LocalDateTime.now(clock);
        Vehicle vehicle = vehicleRepository.lockById(candidate.vehicle().getId())
                .orElseThrow(() -> new NotFoundException("Vehicle", candidate.vehicle().getId()));
        Trip trip = candidate.trip();
        if (trip == null) {
            if (tripRepository.hasOpenTrip(vehicle.getId())) {
                return false;
            }
            trip = new Trip();
            trip.setTripCode(Codes.tripCode(LocalDate.now(clock)));
            trip.setRoute(booking.getRoute());
            trip.setVehicle(vehicle);
            trip.setDriver(candidate.driver());
            trip.setDuty(candidate.duty());
            trip.setExclusiveRide(booking.getRideType() == RideType.EXCLUSIVE);
            trip.setStatus(TripStatus.PLANNED);
            trip.setStartSeq(booking.getFromSeq());
            trip.setCreatedAt(now);
            tripRepository.save(trip);
        } else if (!trip.isOpen()) {
            return false;
        }

        LocalDateTime pickupAt = now.plusMinutes(candidate.etaMinutes());
        if (booking.getScheduledAt() != null && booking.getScheduledAt().isAfter(pickupAt)) {
            pickupAt = booking.getScheduledAt();
        }
        booking.setTrip(trip);
        booking.setStatus(BookingStatus.ASSIGNED);
        booking.setAssignedAt(now);
        booking.setEtaMinutes(candidate.etaMinutes());
        booking.setPromisedPickupAt(pickupAt);
        if (booking.getOtp() == null) {
            booking.setOtp(Codes.otp());
        }
        booking.setOtpAttempts(0);
        booking.setMatchNote(candidate.reason());
        booking.truncateMatchNote();
        booking.setUpdatedAt(now);

        String driverName = candidate.driver().getUser().getFullName();
        notificationService.notifyBooker(booking, NotificationService.BOOKING_CONFIRMED,
                "Cab " + vehicle.getCode() + " is on the way",
                vehicle.getRegistrationNo() + " · " + driverName + " " + candidate.driver().getUser().getPhone()
                        + " · at " + booking.getFromStop().getName() + " in about " + Math.max(1, candidate.etaMinutes())
                        + " min · boarding OTP " + booking.getOtp());
        return true;
    }

    /**
     * Takes the route's matching lock. Callers that insert a booking must call
     * this BEFORE the insert: the insert's foreign-key check takes a shared
     * lock on the route row, and two transactions each holding that shared
     * lock while waiting for the exclusive one deadlock (the loser got a 409).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockRoute(Long routeId) {
        routeRepository.lockById(routeId).orElseThrow(() -> new NotFoundException("Route", routeId));
    }

    private void lockRoute(Booking booking) {
        lockRoute(booking.getRoute().getId());
    }

    private String vehicleCode(Long vehicleId) {
        return vehicleRepository.findById(vehicleId).map(Vehicle::getCode).orElse("Vehicle " + vehicleId);
    }

    private static String noCabNote(MatchOutcome outcome) {
        if (outcome.rejected().isEmpty()) {
            return "No cab is on duty right now. The desk has been shown this request.";
        }
        return "No cab free yet: " + outcome.rejected().stream()
                .map(Rejection::describe)
                .collect(Collectors.joining("; "));
    }

    /**
     * A request nobody could serve within the expiry window is closed rather
     * than left in the queue: a cab turning up hours later for a 09:00 request
     * helps no one. The rider is told to book again.
     */
    private void expireStaleRequests(LocalDateTime now, int expiryMinutes) {
        for (Booking b : bookingRepository.findQueue(List.of(BookingStatus.REQUESTED))) {
            LocalDateTime waitingSince = b.getScheduledAt() != null && b.getScheduledAt().isAfter(b.getCreatedAt())
                    ? b.getScheduledAt() : b.getCreatedAt();
            if (waitingSince.isBefore(now.minusMinutes(expiryMinutes))) {
                b.close(BookingStatus.CANCELLED, "No cab became free within " + expiryMinutes + " min", now);
                notificationService.notifyBooker(b, NotificationService.BOOKING_CANCELLED, "No cab was free",
                        b.getFromStop().getName() + " to " + b.getToStop().getName() + ": no cab became free within "
                                + expiryMinutes + " minutes, so the request was closed. Please book again.");
            }
        }
    }

    /** "Gate 5 wait above 8 min": one alert per stop while riders keep waiting unassigned. */
    private void raiseDemandAlerts(LocalDateTime now) {
        try {
            requiresNew.executeWithoutResult(status -> {
                List<Booking> waiting = bookingRepository.findQueue(List.of(BookingStatus.REQUESTED));
                Map<Stop, List<Booking>> byStop = waiting.stream()
                        .filter(b -> Duration.between(b.getScheduledAt() != null && b.getScheduledAt().isAfter(b.getCreatedAt())
                                ? b.getScheduledAt() : b.getCreatedAt(), now).toMinutes() >= DEMAND_ALERT_WAIT_MINUTES)
                        .collect(Collectors.groupingBy(Booking::getFromStop));
                byStop.forEach((stop, riders) -> alertService.raise(AlertType.DEMAND, AlertSeverity.MEDIUM,
                                stop.getName() + " wait above " + DEMAND_ALERT_WAIT_MINUTES + " min · " + riders.size()
                                        + (riders.size() == 1 ? " rider" : " riders") + " without a cab")
                        .at(stop.getLatitude(), stop.getLongitude())
                        .dedupe("DEMAND:" + stop.getCode(), 30)
                        .save());
            });
        } catch (RuntimeException ex) {
            log.warn("Demand alert check failed: {}", ex.getMessage());
        }
    }
}
