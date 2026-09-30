package com.srmecotech.plantride.desk;

import com.srmecotech.plantride.booking.Booking;
import com.srmecotech.plantride.booking.BookingRepository;
import com.srmecotech.plantride.booking.BookingStatus;
import com.srmecotech.plantride.common.audit.AuditService;
import com.srmecotech.plantride.common.error.BusinessRuleException;
import com.srmecotech.plantride.common.error.NotFoundException;
import com.srmecotech.plantride.common.security.AuthenticatedUser;
import com.srmecotech.plantride.common.settings.SettingsService;
import com.srmecotech.plantride.desk.dto.DeskDtos.CandidateDto;
import com.srmecotech.plantride.desk.dto.DeskDtos.DeskActionResultDto;
import com.srmecotech.plantride.desk.dto.DeskDtos.KpiDto;
import com.srmecotech.plantride.desk.dto.DeskDtos.LiveBoardDto;
import com.srmecotech.plantride.desk.dto.DeskDtos.QueueItemDto;
import com.srmecotech.plantride.desk.dto.DeskDtos.VehicleLiveDto;
import com.srmecotech.plantride.identity.AppUserRepository;
import com.srmecotech.plantride.masterdata.ComplianceService;
import com.srmecotech.plantride.masterdata.Route;
import com.srmecotech.plantride.masterdata.RoutePlan;
import com.srmecotech.plantride.masterdata.RouteResolver;
import com.srmecotech.plantride.masterdata.Vehicle;
import com.srmecotech.plantride.masterdata.VehicleRepository;
import com.srmecotech.plantride.masterdata.VehicleStatus;
import com.srmecotech.plantride.masterdata.dto.MasterDataDtos.DocState;
import com.srmecotech.plantride.matching.Candidate;
import com.srmecotech.plantride.matching.DispatchRequestedEvent;
import com.srmecotech.plantride.matching.DispatchResult;
import com.srmecotech.plantride.matching.DispatchService;
import com.srmecotech.plantride.matching.MatchOutcome;
import com.srmecotech.plantride.matching.MatchRequest;
import com.srmecotech.plantride.matching.MatchingEngine;
import com.srmecotech.plantride.notification.NotificationService;
import com.srmecotech.plantride.safety.AlertService;
import com.srmecotech.plantride.safety.AlertType;
import com.srmecotech.plantride.safety.dto.AlertDto;
import com.srmecotech.plantride.trip.Duty;
import com.srmecotech.plantride.trip.DutyRepository;
import com.srmecotech.plantride.trip.Trip;
import com.srmecotech.plantride.trip.TripRepository;
import com.srmecotech.plantride.trip.TripService;
import com.srmecotech.plantride.trip.TripStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** The transport desk: the whole fleet on one screen, plus the queue it answers for. */
@Service
public class DeskService {

    private static final int ON_TIME_GRACE_MINUTES = 3;

    private final VehicleRepository vehicleRepository;
    private final DutyRepository dutyRepository;
    private final TripRepository tripRepository;
    private final BookingRepository bookingRepository;
    private final AppUserRepository userRepository;
    private final RouteResolver routeResolver;
    private final ComplianceService complianceService;
    private final AlertService alertService;
    private final MatchingEngine matchingEngine;
    private final DispatchService dispatchService;
    private final TripService tripService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final SettingsService settings;
    private final NamedParameterJdbcTemplate jdbc;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public DeskService(VehicleRepository vehicleRepository, DutyRepository dutyRepository,
                       TripRepository tripRepository, BookingRepository bookingRepository,
                       AppUserRepository userRepository, RouteResolver routeResolver,
                       ComplianceService complianceService, AlertService alertService,
                       MatchingEngine matchingEngine, DispatchService dispatchService, TripService tripService,
                       NotificationService notificationService, AuditService auditService, SettingsService settings,
                       NamedParameterJdbcTemplate jdbc, ApplicationEventPublisher events, Clock clock) {
        this.vehicleRepository = vehicleRepository;
        this.dutyRepository = dutyRepository;
        this.tripRepository = tripRepository;
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.routeResolver = routeResolver;
        this.complianceService = complianceService;
        this.alertService = alertService;
        this.matchingEngine = matchingEngine;
        this.dispatchService = dispatchService;
        this.tripService = tripService;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.settings = settings;
        this.jdbc = jdbc;
        this.events = events;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ live board

    @Transactional(readOnly = true)
    public LiveBoardDto board() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Vehicle> vehicles = vehicleRepository.findAllForBoard();
        Map<Long, Duty> dutyByVehicle = new HashMap<>();
        for (Duty d : dutyRepository.findActiveWithVehicleAndDriver()) {
            dutyByVehicle.put(d.getVehicle().getId(), d);
        }
        Map<Long, Trip> tripByVehicle = new HashMap<>();
        for (Trip t : tripRepository.findOpenTrips()) {
            tripByVehicle.put(t.getVehicle().getId(), t);
        }
        Map<Long, List<Booking>> ridersByTrip = tripByVehicle.isEmpty() ? Map.of()
                : bookingRepository.findOnTrips(tripByVehicle.values().stream().map(Trip::getId).toList(), BookingStatus.ON_TRIP)
                .stream().collect(Collectors.groupingBy(b -> b.getTrip().getId()));
        List<AlertDto> alerts = alertService.unresolved();
        Map<String, Set<AlertType>> alertTypesByVehicle = new HashMap<>();
        for (AlertDto a : alerts) {
            if (a.vehicleCode() != null) {
                alertTypesByVehicle.computeIfAbsent(a.vehicleCode(), k -> new HashSet<>()).add(a.type());
            }
        }
        Map<Long, LocalDateTime> lastTripEnd = lastTripEndToday(now.toLocalDate());
        int defaultCap = routeResolver.activePlans().stream()
                .mapToInt(p -> p.route().getMaxPassengers()).min().orElse(3);

        List<VehicleLiveDto> rows = new ArrayList<>();
        int inService = 0;
        for (Vehicle v : vehicles) {
            VehicleLiveDto row = liveRow(v, dutyByVehicle.get(v.getId()), tripByVehicle.get(v.getId()), ridersByTrip,
                    alertTypesByVehicle.getOrDefault(v.getCode(), Set.of()), lastTripEnd.get(v.getId()), defaultCap, now);
            if (Set.of("ON_TRIP", "ASSIGNED", "SHUTTLE").contains(row.status())) {
                inService++;
            }
            rows.add(row);
        }
        rows.sort((a, b) -> Integer.compare(statusOrder(a.status()), statusOrder(b.status())) != 0
                ? Integer.compare(statusOrder(a.status()), statusOrder(b.status()))
                : a.code().compareTo(b.code()));

        List<QueueItemDto> queue = queueItems(now);
        KpiDto kpis = kpis(now, inService, vehicles.size(), alerts, queue.size());
        return new LiveBoardDto(now, kpis, rows, alerts, queue);
    }

    @Transactional(readOnly = true)
    public List<QueueItemDto> queue() {
        return queueItems(LocalDateTime.now(clock));
    }

    // ------------------------------------------------------------------ queue actions

    @Transactional
    public DeskActionResultDto approve(Long bookingId, AuthenticatedUser actor) {
        Booking booking = detailed(bookingId);
        if (booking.getStatus() != BookingStatus.PENDING_APPROVAL) {
            throw new BusinessRuleException("NOT_PENDING", booking.getBookingCode() + " is not waiting for approval.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        booking.setApprovedBy(userRepository.getReferenceById(actor.id()));
        int lead = settings.getInt(SettingsService.DISPATCH_LEAD_MINUTES, 20);
        boolean later = booking.getScheduledAt() != null && booking.getScheduledAt().isAfter(now.plusMinutes(lead));
        booking.setStatus(later ? BookingStatus.SCHEDULED : BookingStatus.REQUESTED);
        booking.setUpdatedAt(now);
        auditService.record(actor, "EXCLUSIVE_APPROVED", "BOOKING", booking.getId(), booking.getBookingCode()
                + " · " + booking.getRiderName() + " · approved by " + actor.fullName());
        notificationService.notifyBooker(booking, NotificationService.APPROVAL, "Exclusive ride approved",
                booking.getFromStop().getName() + " to " + booking.getToStop().getName() + " · approved by the transport desk.");
        String message;
        if (booking.getStatus() == BookingStatus.REQUESTED) {
            DispatchResult result = dispatchService.dispatch(booking);
            message = result.assigned()
                    ? "Approved and assigned to " + result.candidate().vehicle().getCode() + "."
                    : "Approved. No empty cab yet; it will be matched as soon as one frees up.";
        } else {
            message = "Approved. It will be dispatched before the scheduled pickup.";
        }
        return new DeskActionResultDto(message, queueItem(booking, now));
    }

    @Transactional
    public DeskActionResultDto reject(Long bookingId, String reason, AuthenticatedUser actor) {
        Booking booking = detailed(bookingId);
        if (booking.getStatus() != BookingStatus.PENDING_APPROVAL) {
            throw new BusinessRuleException("NOT_PENDING", booking.getBookingCode() + " is not waiting for approval.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        booking.close(BookingStatus.REJECTED, reason.trim(), now);
        auditService.record(actor, "EXCLUSIVE_REJECTED", "BOOKING", booking.getId(),
                booking.getBookingCode() + " · " + reason.trim());
        notificationService.notifyBooker(booking, NotificationService.APPROVAL, "Exclusive ride not approved",
                reason.trim() + ". You can book a shared cab instead.");
        return new DeskActionResultDto("Rejected.", queueItem(booking, now));
    }

    /** Every on-duty cab for this booking: feasible ones with ETA and score, the rest with the rule that failed. */
    @Transactional(readOnly = true)
    public List<CandidateDto> candidates(Long bookingId) {
        Booking booking = detailed(bookingId);
        if (!EnumSet.of(BookingStatus.REQUESTED, BookingStatus.ASSIGNED).contains(booking.getStatus())) {
            throw new BusinessRuleException("NOT_ASSIGNABLE", "Only waiting or assigned bookings can be (re)assigned.");
        }
        RoutePlan plan = routeResolver.planFor(booking.getRoute().getId());
        MatchOutcome outcome = matchingEngine.evaluate(MatchRequest.forBooking(plan, booking));
        List<CandidateDto> result = new ArrayList<>();
        for (Candidate c : outcome.feasible()) {
            result.add(new CandidateDto(c.vehicle().getId(), c.vehicle().getCode(), true, c.etaMinutes(),
                    c.seatsTaken(), c.capacity(), Math.round(c.score() * 100d) / 100d, c.reason()));
        }
        outcome.rejected().forEach(r -> result.add(new CandidateDto(r.vehicleId(), r.vehicleCode(), false, null,
                null, null, null, r.describe())));
        return result;
    }

    /**
     * Desk override: put the booking on a specific cab (re-assigning it if it
     * is already on another one). Hard rules still apply.
     */
    @Transactional
    public DeskActionResultDto assign(Long bookingId, Long vehicleId, AuthenticatedUser actor) {
        Booking booking = detailed(bookingId);
        LocalDateTime now = LocalDateTime.now(clock);
        String from = null;
        if (booking.getStatus() == BookingStatus.ASSIGNED) {
            Trip current = booking.getTrip();
            if (current != null && current.getVehicle().getId().equals(vehicleId)) {
                throw new BusinessRuleException("ALREADY_ASSIGNED", booking.getBookingCode() + " is already on "
                        + current.getVehicle().getCode() + ".");
            }
            from = current == null ? null : current.getVehicle().getCode();
            tripService.returnToQueue(booking);
        } else if (booking.getStatus() != BookingStatus.REQUESTED) {
            throw new BusinessRuleException("NOT_ASSIGNABLE", "Only waiting or assigned bookings can be (re)assigned.");
        }
        Candidate candidate = dispatchService.dispatchTo(booking, vehicleId);
        auditService.record(actor, "BOOKING_ASSIGNED_BY_DESK", "BOOKING", booking.getId(), booking.getBookingCode()
                + (from == null ? "" : " moved from " + from) + " to " + candidate.vehicle().getCode()
                + " by " + actor.fullName() + " · " + candidate.reason());
        return new DeskActionResultDto("Assigned to " + candidate.vehicle().getCode() + ".", queueItem(booking, now));
    }

    @Transactional
    public DeskActionResultDto cancel(Long bookingId, String reason, AuthenticatedUser actor) {
        Booking booking = detailed(bookingId);
        if (!BookingStatus.CANCELLABLE.contains(booking.getStatus())) {
            throw new BusinessRuleException("NOT_CANCELLABLE", booking.getBookingCode() + " cannot be cancelled now ("
                    + booking.getStatus().name().toLowerCase().replace('_', ' ') + ").");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        String why = "Cancelled by desk: " + reason.trim();
        tripService.cancelBooking(booking, why);
        auditService.record(actor, "BOOKING_CANCELLED_BY_DESK", "BOOKING", booking.getId(), booking.getBookingCode() + " · " + why);
        notificationService.notifyBooker(booking, NotificationService.BOOKING_CANCELLED, "Booking cancelled by the transport desk", why);
        return new DeskActionResultDto("Cancelled.", queueItem(booking, now));
    }

    @Transactional
    public String setVehicleStatus(Long vehicleId, VehicleStatus status, String note, AuthenticatedUser actor) {
        Vehicle vehicle = vehicleRepository.lockById(vehicleId).orElseThrow(() -> new NotFoundException("Vehicle", vehicleId));
        if (vehicle.getStatus() == status) {
            return vehicle.getCode() + " is already " + status.name().toLowerCase().replace('_', '-') + ".";
        }
        int moved = 0;
        if (status == VehicleStatus.OFF_ROAD) {
            Trip open = tripRepository.findOpenByVehicleId(vehicleId).orElse(null);
            if (open != null) {
                moved = tripService.abandon(open, "taken off-road by the desk");
            }
        }
        vehicle.setStatus(status);
        auditService.record(actor, status == VehicleStatus.OFF_ROAD ? "VEHICLE_OFF_ROAD" : "VEHICLE_BACK_ON_ROAD",
                "VEHICLE", vehicleId, vehicle.getCode() + (note == null || note.isBlank() ? "" : " · " + note.trim()));
        events.publishEvent(new DispatchRequestedEvent(vehicle.getCode() + " is now " + status));
        return status == VehicleStatus.OFF_ROAD
                ? vehicle.getCode() + " is off-road" + (moved > 0 ? "; " + moved + " rider(s) are being re-matched." : ".")
                : vehicle.getCode() + " is back on the road.";
    }

    // ------------------------------------------------------------------ helpers

    private VehicleLiveDto liveRow(Vehicle v, Duty duty, Trip trip, Map<Long, List<Booking>> ridersByTrip,
                                   Set<AlertType> alertTypes, LocalDateTime lastTripEnd, int defaultCap, LocalDateTime now) {
        List<String> flags = new ArrayList<>();
        if (alertTypes.contains(AlertType.SOS)) {
            flags.add("SOS");
        }
        if (alertTypes.contains(AlertType.OVERSPEED)) {
            flags.add("OVERSPEED");
        }
        boolean docSoon = complianceService.vehicleDocuments(v).stream()
                .anyMatch(d -> d.state() == DocState.EXPIRED || d.daysLeft() <= 7);
        if (docSoon) {
            flags.add("DOC_EXPIRY");
        }
        if (alertTypes.contains(AlertType.BREAKDOWN) || alertTypes.contains(AlertType.ACCIDENT)) {
            flags.add("BREAKDOWN");
        }

        String status;
        String label;
        String routeLabel = null;
        String location;
        String stopName = v.getCurrentStop() == null ? "unknown position" : v.getCurrentStop().getName();
        int riders = 0;
        int capacity = v.isShuttle() ? v.getSeatCapacity() : Math.min(defaultCap, v.getSeatCapacity());
        Long idleMinutes = null;
        var blocked = complianceService.vehicleBlockReason(v);

        if (v.getStatus() == VehicleStatus.OFF_ROAD) {
            status = "OFF_ROAD";
            label = flags.contains("BREAKDOWN") ? "Breakdown" : "Off-road";
            location = "at " + stopName;
        } else if (trip != null) {
            RoutePlan plan = routeResolver.planFor(trip.getRoute().getId());
            List<Booking> onTrip = ridersByTrip.getOrDefault(trip.getId(), List.of());
            riders = onTrip.stream().filter(b -> b.getStatus() == BookingStatus.ONBOARD).mapToInt(Booking::getSeats).sum();
            capacity = trip.isExclusiveRide() ? v.getSeatCapacity() : Math.min(trip.getRoute().getMaxPassengers(), v.getSeatCapacity());
            status = trip.getStatus() == TripStatus.IN_PROGRESS ? "ON_TRIP" : "ASSIGNED";
            label = trip.getStatus() == TripStatus.IN_PROGRESS ? "On trip" : "To pickup";
            routeLabel = trip.getRoute().label();
            int lastSeq = onTrip.stream().mapToInt(Booking::getToSeq).max().orElse(trip.getStartSeq());
            location = plan.stopAt(trip.getStartSeq()).getName() + " → " + plan.stopAt(lastSeq).getName()
                    + (trip.getCurrentSeq() == null ? " · heading to first pickup" : " · at " + plan.stopAt(trip.getCurrentSeq()).getName());
        } else if (blocked.isPresent()) {
            status = "BLOCKED";
            label = "Doc expired";
            location = blocked.get();
        } else if (duty != null && v.isShuttle()) {
            status = "SHUTTLE";
            label = "Shuttle";
            Route home = v.getHomeRoute();
            routeLabel = home == null ? null : home.label() + " loop";
            location = "at " + stopName;
        } else if (duty != null) {
            status = "IDLE";
            label = "Idle";
            LocalDateTime since = lastTripEnd != null && lastTripEnd.isAfter(duty.getStartedAt()) ? lastTripEnd : duty.getStartedAt();
            idleMinutes = Math.max(0, Duration.between(since, now).toMinutes());
            location = "at " + stopName + " · idle " + (idleMinutes >= 60 ? idleMinutes / 60 + "h " + idleMinutes % 60 + "m" : idleMinutes + " min");
        } else {
            status = "OFF_DUTY";
            label = "Off duty";
            location = "parked at " + stopName;
        }

        String driverName = null;
        String driverPhone = null;
        if (duty != null) {
            driverName = duty.getDriver().getUser().getFullName();
            driverPhone = duty.getDriver().getUser().getPhone();
        } else if (trip != null) {
            driverName = trip.getDriver().getUser().getFullName();
            driverPhone = trip.getDriver().getUser().getPhone();
        }
        return new VehicleLiveDto(v.getId(), v.getCode(), v.getRegistrationNo(), v.getVehicleType().name(), status, label,
                flags, routeLabel, location, riders, capacity, driverName, driverPhone,
                trip == null ? null : trip.getId(), trip == null ? null : trip.getTripCode(), idleMinutes, v.getLastPingAt());
    }

    private static int statusOrder(String status) {
        return switch (status) {
            case "ON_TRIP" -> 0;
            case "ASSIGNED" -> 1;
            case "SHUTTLE" -> 2;
            case "IDLE" -> 3;
            case "OFF_ROAD" -> 4;
            case "BLOCKED" -> 5;
            default -> 6;
        };
    }

    private KpiDto kpis(LocalDateTime now, int inService, int fleetSize, List<AlertDto> alerts, int waiting) {
        MapSqlParameterSource params = new MapSqlParameterSource("today", Timestamp.valueOf(now.toLocalDate().atStartOfDay()))
                .addValue("grace", ON_TIME_GRACE_MINUTES);
        Map<String, Object> pickups = jdbc.queryForMap("""
                SELECT COUNT(*) AS boarded,
                       AVG(TIMESTAMPDIFF(SECOND,
                           CASE WHEN scheduled_at IS NOT NULL AND scheduled_at > created_at THEN scheduled_at ELSE created_at END,
                           boarded_at)) / 60 AS avg_wait,
                       SUM(CASE WHEN promised_pickup_at IS NOT NULL
                                 AND boarded_at <= promised_pickup_at + INTERVAL :grace MINUTE THEN 1 ELSE 0 END) AS on_time
                FROM booking
                WHERE boarded_at >= :today
                """, params);
        long boarded = ((Number) pickups.get("boarded")).longValue();
        BigDecimal avgWait = pickups.get("avg_wait") == null ? BigDecimal.ZERO
                : new BigDecimal(pickups.get("avg_wait").toString()).setScale(1, RoundingMode.HALF_UP);
        long onTime = pickups.get("on_time") == null ? 0 : ((Number) pickups.get("on_time")).longValue();

        Map<String, Object> occupancy = jdbc.queryForMap("""
                SELECT COALESCE(SUM(x.riders), 0) AS riders, COALESCE(SUM(x.cap), 0) AS seats
                FROM (SELECT LEAST(r.max_passengers, v.seat_capacity) AS cap,
                             (SELECT COALESCE(SUM(b.seats), 0) FROM booking b
                              WHERE b.trip_id = t.id AND b.status IN ('ONBOARD', 'COMPLETED')) AS riders
                      FROM trip t
                      JOIN route r   ON r.id = t.route_id
                      JOIN vehicle v ON v.id = t.vehicle_id
                      WHERE t.started_at >= :today
                        AND t.status IN ('IN_PROGRESS', 'COMPLETED')
                        AND t.exclusive_ride = FALSE) x
                """, params);
        long seatRiders = ((Number) occupancy.get("riders")).longValue();
        long seats = ((Number) occupancy.get("seats")).longValue();

        int openAlerts = (int) alerts.stream().filter(a -> a.status().name().equals("OPEN")).count();
        return new KpiDto(inService, fleetSize,
                seats == 0 ? 0 : (int) Math.round(100d * seatRiders / seats),
                avgWait,
                boarded == 0 ? 100 : (int) Math.round(100d * onTime / boarded),
                (int) boarded, openAlerts, waiting);
    }

    private Map<Long, LocalDateTime> lastTripEndToday(LocalDate today) {
        Map<Long, LocalDateTime> result = new HashMap<>();
        jdbc.query("""
                SELECT vehicle_id, MAX(ended_at) AS last_end FROM trip
                WHERE ended_at >= :today GROUP BY vehicle_id
                """, new MapSqlParameterSource("today", Timestamp.valueOf(today.atStartOfDay())), rs -> {
            result.put(rs.getLong("vehicle_id"), rs.getTimestamp("last_end").toLocalDateTime());
        });
        return result;
    }

    private List<QueueItemDto> queueItems(LocalDateTime now) {
        return bookingRepository.findQueue(EnumSet.of(BookingStatus.REQUESTED, BookingStatus.PENDING_APPROVAL)).stream()
                .map(b -> queueItem(b, now))
                .toList();
    }

    private QueueItemDto queueItem(Booking b, LocalDateTime now) {
        LocalDateTime waitingSince = b.getScheduledAt() != null && b.getScheduledAt().isAfter(b.getCreatedAt())
                ? b.getScheduledAt() : b.getCreatedAt();
        String personnelNo = b.isForGuest() ? "Guest of " + b.getEmployee().getPersonnelNo() : b.getEmployee().getPersonnelNo();
        return new QueueItemDto(b.getId(), b.getBookingCode(), b.getStatus(), b.getRideType(), b.getRiderName(),
                personnelNo, b.getCostCentre().getCode(), b.getRoute().getCode(), b.getFromStop().getName(),
                b.getToStop().getName(), b.getSeats(), b.getScheduledAt(), b.getCreatedAt(),
                Math.max(0, Duration.between(waitingSince, now).toMinutes()),
                b.getTrip() == null ? null : b.getTrip().getVehicle().getCode(), b.getMatchNote());
    }

    private Booking detailed(Long bookingId) {
        return bookingRepository.findDetailed(bookingId).orElseThrow(() -> new NotFoundException("Booking", bookingId));
    }
}
