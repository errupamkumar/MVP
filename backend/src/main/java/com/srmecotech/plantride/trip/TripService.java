package com.srmecotech.plantride.trip;

import com.srmecotech.plantride.billing.BillingService;
import com.srmecotech.plantride.booking.Booking;
import com.srmecotech.plantride.booking.BookingRepository;
import com.srmecotech.plantride.booking.BookingStatus;
import com.srmecotech.plantride.common.audit.AuditService;
import com.srmecotech.plantride.common.error.BusinessRuleException;
import com.srmecotech.plantride.common.error.ForbiddenOperationException;
import com.srmecotech.plantride.common.error.NotFoundException;
import com.srmecotech.plantride.common.security.AuthenticatedUser;
import com.srmecotech.plantride.common.settings.SettingsService;
import com.srmecotech.plantride.common.util.Money;
import com.srmecotech.plantride.masterdata.Driver;
import com.srmecotech.plantride.masterdata.RoutePlan;
import com.srmecotech.plantride.masterdata.RouteResolver;
import com.srmecotech.plantride.masterdata.Stop;
import com.srmecotech.plantride.masterdata.Vehicle;
import com.srmecotech.plantride.matching.DispatchRequestedEvent;
import com.srmecotech.plantride.notification.NotificationService;
import com.srmecotech.plantride.trip.dto.DriverDtos.TripQueueDto;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * The trip state machine the driver drives: arrive at the next stop (drops
 * complete, pickups are told the cab is there), board riders by OTP, mark
 * no-shows after the configured wait, and close the trip (and bill it) once
 * nobody is left on it.
 */
@Service
public class TripService {

    private final TripRepository tripRepository;
    private final BookingRepository bookingRepository;
    private final RouteResolver routeResolver;
    private final DriverQueueService queueService;
    private final BillingService billingService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final SettingsService settings;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public TripService(TripRepository tripRepository, BookingRepository bookingRepository,
                       RouteResolver routeResolver, DriverQueueService queueService, BillingService billingService,
                       NotificationService notificationService, AuditService auditService,
                       SettingsService settings, ApplicationEventPublisher events, Clock clock) {
        this.tripRepository = tripRepository;
        this.bookingRepository = bookingRepository;
        this.routeResolver = routeResolver;
        this.queueService = queueService;
        this.billingService = billingService;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.settings = settings;
        this.events = events;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ driver actions

    @Transactional
    public Optional<TripQueueDto> arrive(Driver driver, Long tripId, int stopSeq) {
        Trip trip = lockOwnOpenTrip(driver, tripId);
        RoutePlan plan = routeResolver.planFor(trip.getRoute().getId());
        arriveAt(trip, plan, stopSeq);
        return trip.isOpen() ? Optional.of(queueService.build(trip)) : Optional.empty();
    }

    /**
     * Boards one rider after checking their OTP. Wrong codes are counted and
     * the count is committed even though the call fails (noRollbackFor).
     */
    @Transactional(noRollbackFor = OtpMismatchException.class)
    public TripQueueDto board(Driver driver, Long tripId, Long bookingId, String otp) {
        Trip trip = lockOwnOpenTrip(driver, tripId);
        Booking booking = bookingOnTrip(trip, bookingId);
        if (booking.getStatus() == BookingStatus.ONBOARD) {
            return queueService.build(trip);
        }
        if (booking.getStatus() != BookingStatus.ASSIGNED) {
            throw new BusinessRuleException("NOT_BOARDABLE", booking.getRiderName() + "'s booking is "
                    + booking.getStatus().name().toLowerCase().replace('_', ' ') + ".");
        }
        RoutePlan plan = routeResolver.planFor(trip.getRoute().getId());
        String pickup = plan.stopAt(booking.getFromSeq()).getName();
        if (trip.getCurrentSeq() == null || trip.getCurrentSeq() != booking.getFromSeq()) {
            throw new BusinessRuleException("NOT_AT_PICKUP", "Tap \"Arrived\" at " + pickup + " before boarding "
                    + booking.getRiderName() + ".");
        }
        int maxAttempts = settings.getInt(SettingsService.OTP_MAX_ATTEMPTS, 5);
        if (booking.getOtpAttempts() >= maxAttempts) {
            throw new BusinessRuleException("OTP_LOCKED", "Too many wrong codes for " + booking.getRiderName()
                    + ". Call the transport desk to verify the rider.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (!constantTimeEquals(booking.getOtp(), otp)) {
            booking.setOtpAttempts(booking.getOtpAttempts() + 1);
            booking.setUpdatedAt(now);
            int left = maxAttempts - booking.getOtpAttempts();
            throw new OtpMismatchException(left <= 0
                    ? "Wrong code again. " + booking.getRiderName() + "'s boarding is locked; call the desk."
                    : "That code does not match " + booking.getRiderName() + ". " + left + (left == 1 ? " try" : " tries") + " left.");
        }

        booking.setStatus(BookingStatus.ONBOARD);
        booking.setBoardedAt(now);
        booking.setUpdatedAt(now);
        if (trip.getStatus() == TripStatus.PLANNED) {
            trip.setStatus(TripStatus.IN_PROGRESS);
            trip.setStartedAt(now);
        }
        int rideMinutes = plan.minutesBetween(booking.getFromSeq(), booking.getToSeq());
        notificationService.notifyBooker(booking, NotificationService.TRIP_STARTED, "Trip started",
                trip.getVehicle().getCode() + " · drop at " + plan.stopAt(booking.getToSeq()).getName()
                        + " in about " + rideMinutes + " min");
        return queueService.build(trip);
    }

    @Transactional
    public Optional<TripQueueDto> noShow(Driver driver, AuthenticatedUser actor, Long tripId, Long bookingId) {
        Trip trip = lockOwnOpenTrip(driver, tripId);
        Booking booking = bookingOnTrip(trip, bookingId);
        if (booking.getStatus() != BookingStatus.ASSIGNED) {
            throw new BusinessRuleException("NOT_WAITING", booking.getRiderName() + " is not waiting to board.");
        }
        RoutePlan plan = routeResolver.planFor(trip.getRoute().getId());
        String pickup = plan.stopAt(booking.getFromSeq()).getName();
        if (trip.getCurrentSeq() == null || trip.getCurrentSeq() != booking.getFromSeq()) {
            throw new BusinessRuleException("NOT_AT_PICKUP", "Tap \"Arrived\" at " + pickup + " first.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        long waitedSeconds = Duration.between(trip.getLastArrivedAt(), now).getSeconds();
        long requiredSeconds = trip.getRoute().getMaxWaitMin() * 60L;
        if (waitedSeconds < requiredSeconds) {
            long leftMinutes = (long) Math.ceil((requiredSeconds - waitedSeconds) / 60d);
            throw new BusinessRuleException("NO_SHOW_TOO_EARLY", "Wait " + leftMinutes + " more min for "
                    + booking.getRiderName() + " (the rule is " + trip.getRoute().getMaxWaitMin() + " min).");
        }
        booking.close(BookingStatus.NO_SHOW, "Did not board within " + trip.getRoute().getMaxWaitMin()
                + " min at " + pickup, now);
        notificationService.notifyBooker(booking, NotificationService.NO_SHOW, "Marked as a no-show",
                "The cab waited " + trip.getRoute().getMaxWaitMin() + " min at " + pickup + ". "
                        + settings.getInt(SettingsService.NO_SHOW_PAUSE_COUNT, 3) + " no-shows in "
                        + settings.getInt(SettingsService.NO_SHOW_WINDOW_DAYS, 30) + " days pause booking.");
        auditService.record(actor, "NO_SHOW", "BOOKING", booking.getId(),
                booking.getBookingCode() + " · " + booking.getRiderName() + " at " + pickup + " · " + trip.getVehicle().getCode());
        closeIfDone(trip, plan);
        return trip.isOpen() ? Optional.of(queueService.build(trip)) : Optional.empty();
    }

    @Transactional(readOnly = true)
    public Optional<TripQueueDto> currentQueue(Driver driver) {
        return tripRepository.findOpenByDriverId(driver.getId()).map(queueService::build);
    }

    // ------------------------------------------------------------------ used by other modules

    /**
     * Records arrival at route position {@code stopSeq}: riders whose drop this
     * is complete (the geofence close), riders boarding here are told the cab
     * is at their stop, and the trip closes if nobody is left.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void arriveAt(Trip trip, RoutePlan plan, int stopSeq) {
        if (trip.getCurrentSeq() != null && trip.getCurrentSeq() == stopSeq) {
            return; // repeated tap, or the geofence and the driver both reported it
        }
        List<Booking> active = bookingRepository.findOnTrip(trip.getId(), BookingStatus.ON_TRIP);
        Optional<String> blocker = TripItinerary.arrivalBlocker(trip, plan, active);
        if (blocker.isPresent()) {
            throw new BusinessRuleException("CANNOT_ARRIVE", blocker.get());
        }
        Integer next = TripItinerary.nextStoppingSeq(trip, active);
        if (next == null || next != stopSeq) {
            throw new BusinessRuleException("WRONG_STOP", "Your next stop is "
                    + (next == null ? "the end of the trip" : plan.stopAt(next).getName()) + ".");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Stop stop = plan.stopAt(stopSeq);
        trip.setCurrentSeq(stopSeq);
        trip.setLastArrivedAt(now);
        Vehicle vehicle = trip.getVehicle();
        vehicle.setCurrentStop(stop);
        vehicle.setLastLatitude(stop.getLatitude());
        vehicle.setLastLongitude(stop.getLongitude());

        for (Booking b : active) {
            if (b.getStatus() == BookingStatus.ONBOARD && b.getToSeq() == stopSeq) {
                b.setStatus(BookingStatus.COMPLETED);
                b.setDroppedAt(now);
                b.setDistanceKm(Money.km(plan.kmBetween(b.getFromSeq(), b.getToSeq())));
                b.setUpdatedAt(now);
                notificationService.notifyBooker(b, NotificationService.TRIP_COMPLETED,
                        "Arrived at " + stop.getName(),
                        b.getFromStop().getName() + " to " + stop.getName() + " · " + b.getDistanceKm()
                                + " km · rate your ride in the app");
            } else if (b.getStatus() == BookingStatus.ASSIGNED && b.getFromSeq() == stopSeq) {
                notificationService.notifyBooker(b, NotificationService.CAB_ARRIVING,
                        "Cab " + vehicle.getCode() + " is at " + stop.getName(),
                        "Show boarding OTP " + b.getOtp() + " to the driver. The cab waits "
                                + trip.getRoute().getMaxWaitMin() + " min.");
            }
        }
        closeIfDone(trip, plan);
    }

    /** Rider or desk cancelled a booking that may sit on a trip. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelBooking(Booking booking, String reason) {
        LocalDateTime now = LocalDateTime.now(clock);
        Trip trip = booking.getTrip();
        booking.close(BookingStatus.CANCELLED, reason, now);
        if (trip != null && trip.isOpen()) {
            closeIfDone(trip, routeResolver.planFor(trip.getRoute().getId()));
        }
    }

    /** Takes a not-yet-boarded booking off its trip and back into the dispatch queue (desk reassign). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void returnToQueue(Booking booking) {
        Trip trip = booking.getTrip();
        booking.returnToQueue(LocalDateTime.now(clock));
        if (trip != null && trip.isOpen()) {
            closeIfDone(trip, routeResolver.planFor(trip.getRoute().getId()));
        }
    }

    /**
     * The cab cannot continue (breakdown, accident). Everyone still on the
     * trip goes back into the queue; riders already on board are picked up
     * again from the last stop the cab reached.
     *
     * @return riders sent back for re-matching
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int abandon(Trip trip, String reason) {
        RoutePlan plan = routeResolver.planFor(trip.getRoute().getId());
        LocalDateTime now = LocalDateTime.now(clock);
        List<Booking> active = bookingRepository.findOnTrip(trip.getId(), BookingStatus.ON_TRIP);
        Integer current = trip.getCurrentSeq();
        for (Booking b : active) {
            if (b.getStatus() == BookingStatus.ONBOARD && current != null && current > b.getFromSeq()) {
                b.setFromSeq(current);
                b.setFromStop(plan.stopAt(current));
            }
            b.returnToQueue(now);
            b.setMatchNote("Re-matching after " + trip.getVehicle().getCode() + " " + reason);
            notificationService.notifyBooker(b, NotificationService.REMATCHED,
                    "Finding you another cab",
                    trip.getVehicle().getCode() + " cannot continue (" + reason + "). We are re-matching you from "
                            + b.getFromStop().getName() + ". Your boarding OTP stays " + b.getOtp() + ".");
        }
        closeIfDone(trip, plan);
        return active.size();
    }

    /**
     * Closes the trip once nobody is waiting or on board. With at least one
     * completed ride the trip is COMPLETED, costed on GPS route kilometres and
     * allocated to cost centres; otherwise it is CANCELLED.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void closeIfDone(Trip trip, RoutePlan plan) {
        if (!trip.isOpen()) {
            return;
        }
        List<Booking> onTrip = bookingRepository.findOnTrip(trip.getId(),
                EnumSet.of(BookingStatus.ASSIGNED, BookingStatus.ONBOARD, BookingStatus.COMPLETED));
        if (onTrip.stream().anyMatch(b -> BookingStatus.ON_TRIP.contains(b.getStatus()))) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        List<Booking> completed = onTrip.stream()
                .filter(b -> b.getStatus() == BookingStatus.COMPLETED)
                .sorted(Comparator.comparing(Booking::getId))
                .toList();
        trip.setEndedAt(now);
        if (completed.isEmpty()) {
            trip.setStatus(TripStatus.CANCELLED);
            if (trip.getCurrentSeq() != null) {
                trip.setDistanceKm(Money.km(plan.kmBetween(trip.getStartSeq(), trip.getCurrentSeq())));
            }
            auditService.recordSystem("TRIP_CANCELLED", "TRIP", trip.getId(),
                    trip.getTripCode() + " · " + trip.getVehicle().getCode() + " closed with no riders carried");
        } else {
            int lastDrop = completed.stream().mapToInt(Booking::getToSeq).max().orElse(trip.getStartSeq());
            BigDecimal km = Money.km(plan.kmBetween(trip.getStartSeq(), lastDrop));
            trip.setStatus(TripStatus.COMPLETED);
            trip.setDistanceKm(km);
            trip.setCostAmount(Money.rupees(km.multiply(trip.getVehicle().getCostPerKm())));
            billingService.allocate(trip, plan, completed);
            auditService.recordSystem("TRIP_COMPLETED", "TRIP", trip.getId(),
                    trip.getTripCode() + " · " + trip.getVehicle().getCode() + " · " + km + " km · "
                            + completed.size() + " ride(s) · ₹" + trip.getCostAmount());
        }
        // The cab is free again: match anyone waiting.
        events.publishEvent(new DispatchRequestedEvent("trip " + trip.getTripCode() + " closed"));
    }

    // ------------------------------------------------------------------ helpers

    private Trip lockOwnOpenTrip(Driver driver, Long tripId) {
        Trip trip = tripRepository.lockById(tripId).orElseThrow(() -> new NotFoundException("Trip", tripId));
        if (!trip.getDriver().getId().equals(driver.getId())) {
            throw new ForbiddenOperationException("This trip is assigned to another driver.");
        }
        if (!trip.isOpen()) {
            throw new BusinessRuleException("TRIP_CLOSED", "Trip " + trip.getTripCode() + " is already closed.");
        }
        return trip;
    }

    private Booking bookingOnTrip(Trip trip, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking", bookingId));
        if (booking.getTrip() == null || !booking.getTrip().getId().equals(trip.getId())) {
            throw new BusinessRuleException("NOT_ON_TRIP", "That rider is not on this trip.");
        }
        return booking;
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
