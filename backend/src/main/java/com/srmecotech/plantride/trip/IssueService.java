package com.srmecotech.plantride.trip;

import com.srmecotech.plantride.booking.Booking;
import com.srmecotech.plantride.booking.BookingRepository;
import com.srmecotech.plantride.booking.BookingStatus;
import com.srmecotech.plantride.common.audit.AuditService;
import com.srmecotech.plantride.common.error.BusinessRuleException;
import com.srmecotech.plantride.common.security.AuthenticatedUser;
import com.srmecotech.plantride.masterdata.Driver;
import com.srmecotech.plantride.masterdata.Vehicle;
import com.srmecotech.plantride.masterdata.VehicleStatus;
import com.srmecotech.plantride.matching.DispatchRequestedEvent;
import com.srmecotech.plantride.notification.NotificationService;
import com.srmecotech.plantride.safety.Alert;
import com.srmecotech.plantride.safety.AlertService;
import com.srmecotech.plantride.safety.AlertSeverity;
import com.srmecotech.plantride.safety.AlertType;
import com.srmecotech.plantride.trip.dto.DriverDtos.IssueResultDto;
import com.srmecotech.plantride.trip.dto.DriverDtos.IssueType;
import com.srmecotech.plantride.trip.dto.DriverDtos.ReportIssueRequest;
import com.srmecotech.plantride.trip.dto.DriverDtos.SosRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** Driver-reported problems. A breakdown takes the cab off the road and re-matches its riders. */
@Service
public class IssueService {

    private final DriverContext driverContext;
    private final DutyRepository dutyRepository;
    private final TripRepository tripRepository;
    private final BookingRepository bookingRepository;
    private final TripService tripService;
    private final AlertService alertService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;

    public IssueService(DriverContext driverContext, DutyRepository dutyRepository, TripRepository tripRepository,
                        BookingRepository bookingRepository, TripService tripService, AlertService alertService,
                        NotificationService notificationService, AuditService auditService,
                        ApplicationEventPublisher events) {
        this.driverContext = driverContext;
        this.dutyRepository = dutyRepository;
        this.tripRepository = tripRepository;
        this.bookingRepository = bookingRepository;
        this.tripService = tripService;
        this.alertService = alertService;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.events = events;
    }

    @Transactional
    public IssueResultDto report(AuthenticatedUser user, ReportIssueRequest request) {
        Driver driver = driverContext.require(user);
        Duty duty = dutyRepository.findActiveByDriverId(driver.getId())
                .orElseThrow(() -> new BusinessRuleException("NOT_ON_DUTY", "Sign on for duty before reporting an issue."));
        Vehicle vehicle = duty.getVehicle();
        Optional<Trip> openTrip = tripRepository.findOpenByVehicleId(vehicle.getId());
        String where = vehicle.getCurrentStop() == null ? "" : " near " + vehicle.getCurrentStop().getName();

        int rematched = 0;
        AlertType type;
        AlertSeverity severity;
        String message;
        switch (request.type()) {
            case BREAKDOWN, ACCIDENT -> {
                type = request.type() == IssueType.BREAKDOWN ? AlertType.BREAKDOWN : AlertType.ACCIDENT;
                severity = request.type() == IssueType.ACCIDENT ? AlertSeverity.CRITICAL : AlertSeverity.HIGH;
                vehicle.setStatus(VehicleStatus.OFF_ROAD);
                if (openTrip.isPresent()) {
                    rematched = tripService.abandon(openTrip.get(), request.type().name().toLowerCase());
                }
                message = vehicle.getCode() + " " + request.type().name().toLowerCase() + where + " · "
                        + request.description() + " · vehicle off-road"
                        + (rematched > 0 ? " · " + rematched + " rider(s) being re-matched" : "");
            }
            case DELAY -> {
                type = AlertType.DELAY;
                severity = AlertSeverity.MEDIUM;
                message = vehicle.getCode() + " delayed" + where + " · " + request.description();
                openTrip.ifPresent(trip -> notifyRiders(trip, "Your cab is running late", vehicle.getCode()
                        + ": " + request.description()));
            }
            case FUEL -> {
                type = AlertType.FUEL;
                severity = AlertSeverity.LOW;
                BigDecimal litres = request.litres() == null ? BigDecimal.ZERO : request.litres();
                duty.setFuelLitres((duty.getFuelLitres() == null ? BigDecimal.ZERO : duty.getFuelLitres()).add(litres));
                message = vehicle.getCode() + " fuel entry · " + litres + " L · " + request.description();
            }
            default -> {
                type = AlertType.OTHER;
                severity = AlertSeverity.LOW;
                message = vehicle.getCode() + where + " · " + request.description();
            }
        }

        Alert alert = alertService.raise(type, severity, message)
                .vehicle(vehicle)
                .trip(openTrip.orElse(null))
                .raisedBy(driver.getUser())
                .at(vehicle.getLastLatitude(), vehicle.getLastLongitude())
                .save()
                .orElseThrow();
        auditService.record(user, "ISSUE_REPORTED", "VEHICLE", vehicle.getId(), message);
        if (vehicle.getStatus() == VehicleStatus.OFF_ROAD) {
            events.publishEvent(new DispatchRequestedEvent(vehicle.getCode() + " went off-road"));
        }
        String reply = switch (request.type()) {
            case BREAKDOWN, ACCIDENT -> "The desk has been alerted. " + vehicle.getCode() + " is off the road"
                    + (rematched > 0 ? " and " + rematched + " rider(s) are being moved to another cab." : ".");
            case FUEL -> "Fuel entry saved.";
            default -> "Sent to the transport desk.";
        };
        return new IssueResultDto(alert.getId(), reply, rematched);
    }

    @Transactional
    public IssueResultDto sos(AuthenticatedUser user, SosRequest request) {
        Driver driver = driverContext.require(user);
        Optional<Duty> duty = dutyRepository.findActiveByDriverId(driver.getId());
        Vehicle vehicle = duty.map(Duty::getVehicle).orElse(null);
        Trip trip = vehicle == null ? null : tripRepository.findOpenByVehicleId(vehicle.getId()).orElse(null);
        Double lat = request.latitude() != null ? request.latitude() : vehicle == null ? null : vehicle.getLastLatitude();
        Double lng = request.longitude() != null ? request.longitude() : vehicle == null ? null : vehicle.getLastLongitude();
        String message = "SOS from driver " + driver.getUser().getFullName() + " (" + driver.getUser().getPhone() + ")"
                + (vehicle == null ? "" : " in " + vehicle.getCode())
                + (vehicle != null && vehicle.getCurrentStop() != null ? " near " + vehicle.getCurrentStop().getName() : "")
                + (request.note() == null || request.note().isBlank() ? "" : " · " + request.note());
        Alert alert = alertService.raise(AlertType.SOS, AlertSeverity.CRITICAL, message)
                .vehicle(vehicle)
                .trip(trip)
                .raisedBy(driver.getUser())
                .at(lat, lng)
                .save()
                .orElseThrow();
        auditService.record(user, "SOS_RAISED", "ALERT", alert.getId(), message);
        return new IssueResultDto(alert.getId(), "The transport desk and plant security have been alerted.", 0);
    }

    private void notifyRiders(Trip trip, String title, String body) {
        List<Booking> riders = bookingRepository.findOnTrip(trip.getId(), BookingStatus.ON_TRIP);
        riders.forEach(b -> notificationService.notifyBooker(b, NotificationService.BOOKING_WAITING, title, body));
    }
}
