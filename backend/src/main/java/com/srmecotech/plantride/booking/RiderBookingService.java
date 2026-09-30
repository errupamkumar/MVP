package com.srmecotech.plantride.booking;

import com.srmecotech.plantride.booking.dto.RiderDtos.BookingDto;
import com.srmecotech.plantride.booking.dto.RiderDtos.BookingSummaryDto;
import com.srmecotech.plantride.booking.dto.RiderDtos.CreateBookingRequest;
import com.srmecotech.plantride.booking.dto.RiderDtos.EmployeeProfileDto;
import com.srmecotech.plantride.booking.dto.RiderDtos.RateBookingRequest;
import com.srmecotech.plantride.booking.dto.RiderDtos.RatingTag;
import com.srmecotech.plantride.booking.dto.RiderDtos.RideOptionDto;
import com.srmecotech.plantride.booking.dto.RiderDtos.RideOptionsRequest;
import com.srmecotech.plantride.booking.dto.RiderDtos.RideOptionsResponse;
import com.srmecotech.plantride.booking.dto.RiderDtos.RiderHomeDto;
import com.srmecotech.plantride.booking.dto.RiderDtos.RiderSosRequest;
import com.srmecotech.plantride.booking.dto.RiderDtos.SavedPlaceDto;
import com.srmecotech.plantride.booking.dto.RiderDtos.ShuttleInfoDto;
import com.srmecotech.plantride.booking.dto.RiderDtos.SosResultDto;
import com.srmecotech.plantride.common.audit.AuditService;
import com.srmecotech.plantride.common.error.BusinessRuleException;
import com.srmecotech.plantride.common.error.ForbiddenOperationException;
import com.srmecotech.plantride.common.error.InvalidRequestException;
import com.srmecotech.plantride.common.error.NotFoundException;
import com.srmecotech.plantride.common.security.AuthenticatedUser;
import com.srmecotech.plantride.common.settings.SettingsService;
import com.srmecotech.plantride.common.util.Codes;
import com.srmecotech.plantride.identity.AppUser;
import com.srmecotech.plantride.identity.AppUserRepository;
import com.srmecotech.plantride.identity.Employee;
import com.srmecotech.plantride.identity.EmployeeRepository;
import com.srmecotech.plantride.masterdata.ResolvedRide;
import com.srmecotech.plantride.masterdata.Route;
import com.srmecotech.plantride.masterdata.RoutePlan;
import com.srmecotech.plantride.masterdata.RouteResolver;
import com.srmecotech.plantride.masterdata.Stop;
import com.srmecotech.plantride.masterdata.Vehicle;
import com.srmecotech.plantride.masterdata.dto.MasterDataDtos.DestinationDto;
import com.srmecotech.plantride.masterdata.dto.MasterDataDtos.RouteRef;
import com.srmecotech.plantride.masterdata.dto.MasterDataDtos.StopRef;
import com.srmecotech.plantride.matching.Candidate;
import com.srmecotech.plantride.matching.DispatchService;
import com.srmecotech.plantride.matching.MatchOutcome;
import com.srmecotech.plantride.matching.MatchRequest;
import com.srmecotech.plantride.matching.MatchingEngine;
import com.srmecotech.plantride.notification.NotificationService;
import com.srmecotech.plantride.safety.Alert;
import com.srmecotech.plantride.safety.AlertService;
import com.srmecotech.plantride.safety.AlertSeverity;
import com.srmecotech.plantride.safety.AlertType;
import com.srmecotech.plantride.trip.Duty;
import com.srmecotech.plantride.trip.DutyRepository;
import com.srmecotech.plantride.trip.Trip;
import com.srmecotech.plantride.trip.TripService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RiderBookingService {

    private static final int IMMEDIATE_HORIZON_MINUTES = 60;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH);
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final EmployeeRepository employeeRepository;
    private final AppUserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final DutyRepository dutyRepository;
    private final RouteResolver routeResolver;
    private final MatchingEngine matchingEngine;
    private final DispatchService dispatchService;
    private final TripService tripService;
    private final BookingMapper mapper;
    private final NotificationService notificationService;
    private final AlertService alertService;
    private final AuditService auditService;
    private final SettingsService settings;
    private final Clock clock;

    public RiderBookingService(EmployeeRepository employeeRepository, AppUserRepository userRepository,
                               BookingRepository bookingRepository,
                               DutyRepository dutyRepository, RouteResolver routeResolver,
                               MatchingEngine matchingEngine, DispatchService dispatchService,
                               TripService tripService, BookingMapper mapper,
                               NotificationService notificationService, AlertService alertService,
                               AuditService auditService, SettingsService settings, Clock clock) {
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
        this.dutyRepository = dutyRepository;
        this.routeResolver = routeResolver;
        this.matchingEngine = matchingEngine;
        this.dispatchService = dispatchService;
        this.tripService = tripService;
        this.mapper = mapper;
        this.notificationService = notificationService;
        this.alertService = alertService;
        this.auditService = auditService;
        this.settings = settings;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ profile & home

    @Transactional(readOnly = true)
    public EmployeeProfileDto profile(AuthenticatedUser user) {
        return profileOf(employee(user));
    }

    @Transactional(readOnly = true)
    public RiderHomeDto home(AuthenticatedUser user) {
        Employee employee = employee(user);
        LocalDateTime now = LocalDateTime.now(clock);

        BookingDto active = bookingRepository.findLiveForEmployee(employee.getId(), PageRequest.of(0, 1)).stream()
                .findFirst().map(mapper::toDto).orElse(null);
        long upcoming = bookingRepository.findForEmployee(employee.getId(), BookingStatus.ACTIVE, PageRequest.of(0, 50)).size();

        List<Booking> recent = bookingRepository.findCompletedSince(employee.getId(), now.minusDays(30));
        Map<Stop, List<Booking>> byDestination = recent.stream()
                .collect(Collectors.groupingBy(Booking::getToStop, LinkedHashMap::new, Collectors.toList()));
        List<SavedPlaceDto> saved = byDestination.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<Stop, List<Booking>> e) -> e.getValue().size()).reversed())
                .limit(3)
                .map(e -> {
                    Booking last = e.getValue().get(0);
                    String hint = e.getValue().size() > 1
                            ? e.getValue().size() + " trips this month"
                            : "Last trip · " + last.getDroppedAt().format(DAY);
                    return new SavedPlaceDto(e.getKey().getId(), e.getKey().getName(), hint, e.getValue().size());
                })
                .toList();
        Long defaultFrom = recent.stream()
                .collect(Collectors.groupingBy(b -> b.getFromStop().getId(), Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        return new RiderHomeDto(profileOf(employee), active, upcoming, saved, defaultFrom,
                defaultFrom == null ? null : nextShuttleAt(defaultFrom, now),
                notificationService.unreadCount(user.id()));
    }

    @Transactional(readOnly = true)
    public List<DestinationDto> destinations(Long fromStopId) {
        return routeResolver.destinations(fromStopId);
    }

    // ------------------------------------------------------------------ ride options

    @Transactional(readOnly = true)
    public RideOptionsResponse rideOptions(AuthenticatedUser user, RideOptionsRequest request) {
        Employee employee = employee(user);
        LocalDateTime now = LocalDateTime.now(clock);
        ResolvedRide ride = routeResolver.resolve(request.fromStopId(), request.toStopId());
        RoutePlan plan = ride.plan();
        Route route = plan.route();
        int seats = request.seats() == null ? 1 : request.seats();

        List<RideOptionDto> options = new ArrayList<>();

        Optional<ShuttleInfoDto> shuttle = shuttleFor(route, plan, ride.fromSeq(), now);
        shuttle.ifPresent(s -> options.add(new RideOptionDto("SHUTTLE", false,
                "Shuttle " + s.vehicleCode(),
                s.firstRunAt() != null
                        ? "First run " + s.firstRunAt() + " at " + s.stopName() + ", then every " + s.frequencyMin() + " min"
                        : s.routeLabel() + " loop · every " + s.frequencyMin() + " min · no booking needed",
                s.firstRunAt() != null ? null : s.minutesToNext(), null, null, s.seatCapacity(), s.vehicleCode(), false,
                "Walk to " + s.stopName() + "; the shuttle runs to a timetable.")));

        if (seats > route.getMaxPassengers()) {
            options.add(new RideOptionDto("SHARED", false, "Shared cab", "Groups over " + route.getMaxPassengers()
                    + " need an exclusive ride", null, null, null, route.getMaxPassengers(), null, false,
                    "Pick-up runs carry " + route.getMaxPassengers() + " riders max."));
        } else {
            MatchOutcome shared = matchingEngine.evaluate(new MatchRequest(plan, ride.fromSeq(), ride.toSeq(), seats,
                    RideType.SHARED, null, null));
            Optional<Candidate> best = shared.best();
            if (best.isPresent()) {
                Candidate c = best.get();
                options.add(new RideOptionDto("SHARED", true,
                        "Shared cab " + c.vehicle().getCode(),
                        c.seatsTaken() == 0 ? "Empty cab · " + c.capacity() + " seats"
                                : c.seatsTaken() + " of " + c.capacity() + " seats taken",
                        c.etaMinutes(), c.seatsTaken(), c.seatsFree(), c.capacity(), c.vehicle().getCode(), false,
                        null));
            } else {
                options.add(new RideOptionDto("SHARED", true, "Shared cab",
                        "Every cab is busy or full · you get the next free one", null, null, null,
                        route.getMaxPassengers(), null, false,
                        "Your request waits in the queue and is matched automatically."));
            }
        }

        MatchOutcome exclusive = matchingEngine.evaluate(new MatchRequest(plan, ride.fromSeq(), ride.toSeq(), seats,
                RideType.EXCLUSIVE, null, null));
        Optional<Candidate> bestExclusive = exclusive.best();
        boolean needsApproval = !employee.isExclusiveEligible();
        options.add(new RideOptionDto("EXCLUSIVE", true,
                "Exclusive cab",
                needsApproval ? "Needs approval from your HOD" : "Released by grade rule",
                bestExclusive.map(Candidate::etaMinutes).orElse(null),
                0, bestExclusive.map(Candidate::capacity).orElse(null), bestExclusive.map(Candidate::capacity).orElse(null),
                bestExclusive.map(c -> c.vehicle().getCode()).orElse(null), needsApproval,
                bestExclusive.isEmpty() ? "No empty cab right now; it is matched when one frees up." : null));

        return new RideOptionsResponse(RouteRef.of(route), StopRef.of(ride.from()), StopRef.of(ride.to()), ride.km(),
                ride.minutes(), route.getMaxPassengers(), employee.getCostCentre().getCode(),
                employee.getCostCentre().getName(), request.scheduledAt(), options);
    }

    // ------------------------------------------------------------------ booking lifecycle

    @Transactional
    public BookingDto create(AuthenticatedUser user, CreateBookingRequest request, String idempotencyKey) {
        Employee employee = employee(user);
        String key = normaliseKey(idempotencyKey);
        if (key != null) {
            Optional<Booking> existing = bookingRepository.findByClientRequestId(key);
            if (existing.isPresent()) {
                if (!existing.get().getEmployee().getId().equals(employee.getId())) {
                    throw new BusinessRuleException("IDEMPOTENCY_KEY_REUSED", "This request key belongs to another booking.");
                }
                return mapper.toDto(existing.get());
            }
        }

        LocalDateTime now = LocalDateTime.now(clock);
        long noShows = noShowsInWindow(employee, now);
        int pauseAt = settings.getInt(SettingsService.NO_SHOW_PAUSE_COUNT, 3);
        if (noShows >= pauseAt) {
            throw new BusinessRuleException("BOOKING_PAUSED", "Booking is paused: " + noShows + " no-shows in the last "
                    + settings.getInt(SettingsService.NO_SHOW_WINDOW_DAYS, 30)
                    + " days. Contact the transport desk to restore it.");
        }

        ResolvedRide ride = routeResolver.resolve(request.fromStopId(), request.toStopId());
        Route route = ride.route();
        if (request.rideType() == RideType.SHARED && request.seats() > route.getMaxPassengers()) {
            throw new BusinessRuleException("GROUP_TOO_LARGE", "A group of " + request.seats() + " is larger than the "
                    + route.getMaxPassengers() + "-rider cap. Book an exclusive ride or split the group.");
        }

        LocalDateTime scheduledAt = request.scheduledAt();
        if (scheduledAt != null) {
            int maxDays = settings.getInt(SettingsService.MAX_ADVANCE_BOOKING_DAYS, 7);
            if (scheduledAt.isBefore(now.minusMinutes(2))) {
                throw new InvalidRequestException("BAD_TIME", "The pickup time is in the past.");
            }
            if (scheduledAt.isAfter(now.plusDays(maxDays))) {
                throw new InvalidRequestException("BAD_TIME", "Rides can be booked up to " + maxDays + " days ahead.");
            }
            if (!scheduledAt.isAfter(now.plusMinutes(5))) {
                scheduledAt = null; // within 5 minutes is simply "now"
            }
        }
        boolean immediate = scheduledAt == null || !scheduledAt.isAfter(now.plusMinutes(IMMEDIATE_HORIZON_MINUTES));
        if (immediate && !request.forGuest()
                && bookingRepository.hasOpenImmediateRide(employee.getId(), now.plusMinutes(IMMEDIATE_HORIZON_MINUTES))) {
            throw new BusinessRuleException("ACTIVE_RIDE_EXISTS",
                    "You already have a ride in progress. Cancel it before booking another.");
        }

        // Lock before inserting: see DispatchService.lockRoute for the deadlock this avoids.
        dispatchService.lockRoute(route.getId());

        AppUser booker = userRepository.getReferenceById(user.id());
        Booking booking = new Booking();
        booking.setBookingCode(uniqueBookingCode());
        booking.setTrackingToken(uniqueTrackingToken());
        booking.setClientRequestId(key);
        booking.setEmployee(employee);
        booking.setBookedBy(booker);
        booking.setRiderName(request.riderName().trim());
        booking.setRiderPhone(request.riderPhone().trim());
        booking.setRiderEmail(request.riderEmail() == null || request.riderEmail().isBlank() ? null : request.riderEmail().trim());
        booking.setForGuest(request.forGuest());
        booking.setRoute(route);
        booking.setFromStop(ride.from());
        booking.setToStop(ride.to());
        booking.setFromSeq(ride.fromSeq());
        booking.setToSeq(ride.toSeq());
        booking.setRideType(request.rideType());
        booking.setSeats(request.seats());
        booking.setScheduledAt(scheduledAt);
        booking.setCostCentre(employee.getCostCentre());
        booking.setCreatedAt(now);
        booking.setUpdatedAt(now);

        int lead = settings.getInt(SettingsService.DISPATCH_LEAD_MINUTES, 20);
        if (request.rideType() == RideType.EXCLUSIVE && !employee.isExclusiveEligible()) {
            booking.setStatus(BookingStatus.PENDING_APPROVAL);
        } else if (scheduledAt != null && scheduledAt.isAfter(now.plusMinutes(lead))) {
            booking.setStatus(BookingStatus.SCHEDULED);
        } else {
            booking.setStatus(BookingStatus.REQUESTED);
        }
        if (request.rideType() == RideType.EXCLUSIVE && employee.isExclusiveEligible()) {
            booking.setApprovedBy(booker);
            booking.setMatchNote("Exclusive ride released by grade rule (" + employee.getGrade() + ")");
        }
        bookingRepository.saveAndFlush(booking);
        auditService.record(user, "BOOKING_CREATED", "BOOKING", booking.getId(), booking.getBookingCode() + " · "
                + ride.from().getName() + " to " + ride.to().getName() + " · " + request.rideType()
                + (request.forGuest() ? " · guest " + booking.getRiderName() : "")
                + (scheduledAt != null ? " · for " + scheduledAt.format(WHEN) : ""));

        switch (booking.getStatus()) {
            case REQUESTED -> {
                boolean assigned = dispatchService.dispatch(booking).assigned();
                if (!assigned) {
                    notificationService.notifyBooker(booking, NotificationService.BOOKING_WAITING,
                            "Finding you a cab", ride.from().getName() + " to " + ride.to().getName()
                                    + ". Every cab is busy or full; you get the next free one automatically.");
                }
            }
            case SCHEDULED -> notificationService.notifyBooker(booking, NotificationService.BOOKING_CONFIRMED,
                    "Ride scheduled for " + scheduledAt.format(WHEN), ride.from().getName() + " to " + ride.to().getName()
                            + ". A cab is assigned " + lead + " minutes before pickup.");
            case PENDING_APPROVAL -> notificationService.notifyBooker(booking, NotificationService.APPROVAL,
                    "Exclusive ride sent for approval", ride.from().getName() + " to " + ride.to().getName()
                            + ". You will be notified when the transport desk approves it.");
            default -> {
            }
        }
        return mapper.toDto(booking);
    }

    @Transactional(readOnly = true)
    public List<BookingSummaryDto> list(AuthenticatedUser user, String scope) {
        Employee employee = employee(user);
        Set<BookingStatus> statuses = switch (scope == null ? "upcoming" : scope.toLowerCase(Locale.ROOT)) {
            case "upcoming" -> BookingStatus.ACTIVE;
            case "past" -> EnumSet.of(BookingStatus.COMPLETED);
            case "cancelled" -> EnumSet.of(BookingStatus.CANCELLED, BookingStatus.NO_SHOW, BookingStatus.REJECTED);
            default -> throw new InvalidRequestException("BAD_SCOPE", "Scope must be upcoming, past or cancelled.");
        };
        return bookingRepository.findForEmployee(employee.getId(), statuses, PageRequest.of(0, 50)).stream()
                .map(mapper::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public BookingDto get(AuthenticatedUser user, Long bookingId) {
        return mapper.toDto(owned(user, bookingId));
    }

    @Transactional
    public BookingDto cancel(AuthenticatedUser user, Long bookingId, String reason) {
        Booking booking = owned(user, bookingId);
        if (!BookingStatus.CANCELLABLE.contains(booking.getStatus())) {
            throw new BusinessRuleException("NOT_CANCELLABLE", booking.getStatus() == BookingStatus.ONBOARD
                    ? "You are already on board; the ride cannot be cancelled now."
                    : "This booking is already " + booking.getStatus().name().toLowerCase().replace('_', ' ') + ".");
        }
        String why = reason == null || reason.isBlank() ? "Cancelled by rider" : reason.trim();
        tripService.cancelBooking(booking, why);
        auditService.record(user, "BOOKING_CANCELLED", "BOOKING", booking.getId(), booking.getBookingCode() + " · " + why);
        notificationService.notifyBooker(booking, NotificationService.BOOKING_CANCELLED, "Booking cancelled",
                booking.getFromStop().getName() + " to " + booking.getToStop().getName() + " · " + booking.getBookingCode());
        return mapper.toDto(booking);
    }

    @Transactional
    public BookingDto rate(AuthenticatedUser user, Long bookingId, RateBookingRequest request) {
        Booking booking = owned(user, bookingId);
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new BusinessRuleException("NOT_RATEABLE", "Only completed rides can be rated.");
        }
        if (booking.getRating() != null) {
            throw new BusinessRuleException("ALREADY_RATED", "This ride has already been rated.");
        }
        booking.setRating(request.rating());
        booking.setRatingTags(request.tags() == null || request.tags().isEmpty() ? null
                : request.tags().stream().distinct().map(RatingTag::name).collect(Collectors.joining(",")));
        booking.setRatingNote(request.note() == null || request.note().isBlank() ? null : request.note().trim());
        booking.setUpdatedAt(LocalDateTime.now(clock));
        return mapper.toDto(booking);
    }

    @Transactional
    public SosResultDto sos(AuthenticatedUser user, RiderSosRequest request) {
        Employee employee = employee(user);
        Booking booking = request.bookingId() == null ? null : owned(user, request.bookingId());
        Trip trip = booking == null ? null : booking.getTrip();
        Vehicle vehicle = trip == null ? null : trip.getVehicle();
        Double lat = request.latitude() != null ? request.latitude() : vehicle == null ? null : vehicle.getLastLatitude();
        Double lng = request.longitude() != null ? request.longitude() : vehicle == null ? null : vehicle.getLastLongitude();
        String message = "SOS from rider " + employee.getUser().getFullName() + " (" + employee.getPersonnelNo() + ", "
                + employee.getUser().getPhone() + ")"
                + (vehicle == null ? "" : " in " + vehicle.getCode())
                + (vehicle != null && vehicle.getCurrentStop() != null ? " near " + vehicle.getCurrentStop().getName() : "")
                + (request.note() == null || request.note().isBlank() ? "" : " · " + request.note().trim());
        Alert alert = alertService.raise(AlertType.SOS, AlertSeverity.CRITICAL, message)
                .vehicle(vehicle)
                .trip(trip)
                .booking(booking)
                .raisedBy(employee.getUser())
                .at(lat, lng)
                .save()
                .orElseThrow();
        auditService.record(user, "SOS_RAISED", "ALERT", alert.getId(), message);
        return new SosResultDto(alert.getId(), "The transport desk and plant security have been alerted with your cab's location.");
    }

    // ------------------------------------------------------------------ helpers

    private Employee employee(AuthenticatedUser user) {
        return employeeRepository.findByUserId(user.id())
                .orElseThrow(() -> new ForbiddenOperationException("This login has no employee profile."));
    }

    private Booking owned(AuthenticatedUser user, Long bookingId) {
        Booking booking = bookingRepository.findDetailed(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking", bookingId));
        if (!booking.getBookedBy().getId().equals(user.id())) {
            // Same answer as "not found": do not confirm that someone else's booking exists.
            throw new NotFoundException("Booking", bookingId);
        }
        return booking;
    }

    private EmployeeProfileDto profileOf(Employee employee) {
        LocalDateTime now = LocalDateTime.now(clock);
        long noShows = noShowsInWindow(employee, now);
        return new EmployeeProfileDto(employee.getId(), employee.getPersonnelNo(), employee.getUser().getFullName(),
                employee.getUser().getEmail(), employee.getUser().getPhone(), employee.getDepartment(),
                employee.getGrade(), employee.getCostCentre().getCode(), employee.getCostCentre().getName(),
                employee.isExclusiveEligible(),
                noShows >= settings.getInt(SettingsService.NO_SHOW_PAUSE_COUNT, 3), noShows);
    }

    private long noShowsInWindow(Employee employee, LocalDateTime now) {
        int windowDays = settings.getInt(SettingsService.NO_SHOW_WINDOW_DAYS, 30);
        return bookingRepository.countNoShowsSince(employee.getId(), now.minusDays(windowDays));
    }

    private ShuttleInfoDto nextShuttleAt(Long stopId, LocalDateTime now) {
        for (RoutePlan plan : routeResolver.activePlans()) {
            List<Integer> seqs = plan.seqsOfStop(stopId);
            if (seqs.isEmpty()) {
                continue;
            }
            Optional<ShuttleInfoDto> info = shuttleFor(plan.route(), plan, seqs.get(0), now);
            if (info.isPresent()) {
                return info.get();
            }
        }
        return null;
    }

    /**
     * The next timetabled shuttle at a stop. Departures leave the first stop
     * every frequency minutes from first_departure; the shuttle reaches later
     * stops after the configured leg times.
     */
    private Optional<ShuttleInfoDto> shuttleFor(Route route, RoutePlan plan, int fromSeq, LocalDateTime now) {
        if (route.getFrequencyMin() == null || route.getFrequencyMin() <= 0) {
            return Optional.empty();
        }
        Optional<Duty> shuttleDuty = dutyRepository.findActiveWithVehicleAndDriver().stream()
                .filter(d -> d.getVehicle().isShuttle())
                .filter(d -> d.getVehicle().getHomeRoute() != null && d.getVehicle().getHomeRoute().getId().equals(route.getId()))
                .findFirst();
        if (shuttleDuty.isEmpty()) {
            return Optional.empty();
        }
        int frequency = route.getFrequencyMin();
        int offset = plan.minutesBetween(plan.stops().get(0).getSeq(), fromSeq);
        LocalDateTime firstAtStop = now.toLocalDate().atTime(route.getFirstDeparture()).plusMinutes(offset);
        long sinceFirst = Duration.between(firstAtStop, now).toMinutes();
        boolean beforeService = sinceFirst < 0;
        int wait = beforeService ? (int) -sinceFirst : (int) ((frequency - (sinceFirst % frequency)) % frequency);
        Vehicle shuttle = shuttleDuty.get().getVehicle();
        Stop stop = plan.stopAt(fromSeq);
        return Optional.of(new ShuttleInfoDto(shuttle.getCode(), "Route " + route.getCode().replace("R", ""),
                stop.getId(), stop.getName(), wait, frequency, shuttle.getSeatCapacity(),
                beforeService ? firstAtStop.toLocalTime().format(HH_MM) : null));
    }

    private String uniqueBookingCode() {
        for (int i = 0; i < 10; i++) {
            String code = Codes.bookingCode();
            if (!bookingRepository.existsByBookingCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a unique booking code");
    }

    private String uniqueTrackingToken() {
        for (int i = 0; i < 10; i++) {
            String token = Codes.trackingToken();
            if (!bookingRepository.existsByTrackingToken(token)) {
                return token;
            }
        }
        throw new IllegalStateException("Could not generate a unique tracking token");
    }

    private static String normaliseKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String trimmed = key.trim();
        if (trimmed.length() > 64) {
            throw new InvalidRequestException("BAD_IDEMPOTENCY_KEY", "Idempotency-Key must be at most 64 characters.");
        }
        return trimmed;
    }
}
