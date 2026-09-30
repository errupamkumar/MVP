package com.srmecotech.plantride.trip;

import com.srmecotech.plantride.booking.BookingRepository;
import com.srmecotech.plantride.common.audit.AuditService;
import com.srmecotech.plantride.common.error.BusinessRuleException;
import com.srmecotech.plantride.common.error.InvalidRequestException;
import com.srmecotech.plantride.common.error.NotFoundException;
import com.srmecotech.plantride.common.security.AuthenticatedUser;
import com.srmecotech.plantride.common.util.Money;
import com.srmecotech.plantride.masterdata.ComplianceService;
import com.srmecotech.plantride.masterdata.Driver;
import com.srmecotech.plantride.masterdata.Vehicle;
import com.srmecotech.plantride.masterdata.VehicleRepository;
import com.srmecotech.plantride.masterdata.VehicleStatus;
import com.srmecotech.plantride.masterdata.dto.MasterDataDtos.VehicleRef;
import com.srmecotech.plantride.matching.DispatchRequestedEvent;
import com.srmecotech.plantride.trip.dto.DriverDtos;
import com.srmecotech.plantride.trip.dto.DriverDtos.DriverProfileDto;
import com.srmecotech.plantride.trip.dto.DriverDtos.DutyDto;
import com.srmecotech.plantride.trip.dto.DriverDtos.DutySummaryDto;
import com.srmecotech.plantride.trip.dto.DriverDtos.EndDutyRequest;
import com.srmecotech.plantride.trip.dto.DriverDtos.StartDutyRequest;
import com.srmecotech.plantride.trip.dto.DriverDtos.VehicleOptionDto;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sign-on and sign-off. Compliance is enforced at sign-on, not discovered at
 * the gate: an expired licence, gate pass, insurance, fitness or PUC stops
 * the duty from starting.
 */
@Service
public class DutyService {

    private static final Map<String, String> CHECK_LABELS = Map.of(
            "TYRES", "Tyres and pressure",
            "LIGHTS", "Lights and indicators",
            "BELTS", "Seat belts (all seats)",
            "FUEL", "Fuel level",
            "FIRST_AID", "First-aid kit and extinguisher");

    private final DriverContext driverContext;
    private final DutyRepository dutyRepository;
    private final VehicleRepository vehicleRepository;
    private final TripRepository tripRepository;
    private final BookingRepository bookingRepository;
    private final ComplianceService complianceService;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public DutyService(DriverContext driverContext, DutyRepository dutyRepository, VehicleRepository vehicleRepository,
                       TripRepository tripRepository, BookingRepository bookingRepository,
                       ComplianceService complianceService, AuditService auditService,
                       ApplicationEventPublisher events, Clock clock) {
        this.driverContext = driverContext;
        this.dutyRepository = dutyRepository;
        this.vehicleRepository = vehicleRepository;
        this.tripRepository = tripRepository;
        this.bookingRepository = bookingRepository;
        this.complianceService = complianceService;
        this.auditService = auditService;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DriverProfileDto profile(AuthenticatedUser user) {
        Driver driver = driverContext.require(user);
        Optional<Duty> duty = dutyRepository.findActiveByDriverId(driver.getId());
        return new DriverProfileDto(
                driver.getId(),
                driver.getDriverCode(),
                driver.getUser().getFullName(),
                driver.getUser().getPhone(),
                driver.getVendor() == null ? "LHS own fleet" : driver.getVendor().getName(),
                driver.getGatesAllowed(),
                driver.getMaxDutyMinutes(),
                complianceService.driverDocuments(driver),
                complianceService.driverBlockReason(driver).orElse(null),
                driver.getDefaultVehicle() == null ? null : driver.getDefaultVehicle().getId(),
                duty.map(d -> toDto(d, driver)).orElse(null));
    }

    /** Vehicles this driver may take: own fleet for own drivers, their vendor's cabs for vendor drivers. */
    @Transactional(readOnly = true)
    public List<VehicleOptionDto> vehicleOptions(AuthenticatedUser user) {
        Driver driver = driverContext.require(user);
        Map<Long, String> onDutyWith = new HashMap<>();
        for (Duty d : dutyRepository.findActiveWithVehicleAndDriver()) {
            onDutyWith.put(d.getVehicle().getId(), d.getDriver().getUser().getFullName());
        }
        Long defaultId = driver.getDefaultVehicle() == null ? null : driver.getDefaultVehicle().getId();
        return vehicleRepository.findAllForBoard().stream()
                .filter(driver::mayDrive)
                .sorted(Comparator.comparing((Vehicle v) -> !v.getId().equals(defaultId)).thenComparing(Vehicle::getCode))
                .map(v -> {
                    String blocked = null;
                    if (v.getStatus() == VehicleStatus.OFF_ROAD) {
                        blocked = v.getCode() + " is off-road.";
                    } else if (complianceService.vehicleBlockReason(v).isPresent()) {
                        blocked = complianceService.vehicleBlockReason(v).get();
                    } else if (onDutyWith.containsKey(v.getId())) {
                        blocked = "On duty with " + onDutyWith.get(v.getId()) + ".";
                    }
                    return new VehicleOptionDto(v.getId(), v.getCode(), v.getRegistrationNo(), v.getVehicleType().name(),
                            v.getSeatCapacity(), v.getVendor() == null ? "LHS own fleet" : v.getVendor().getName(),
                            v.getOdometerKm(), v.getCurrentStop() == null ? null : v.getCurrentStop().getName(),
                            blocked == null, blocked, complianceService.vehicleDocuments(v));
                })
                .toList();
    }

    @Transactional
    public DutyDto start(AuthenticatedUser user, StartDutyRequest request) {
        Driver driver = driverContext.require(user);
        if (dutyRepository.findActiveByDriverId(driver.getId()).isPresent()) {
            throw new BusinessRuleException("ALREADY_ON_DUTY", "You are already signed on. Sign off before taking another vehicle.");
        }
        complianceService.driverBlockReason(driver).ifPresent(reason -> {
            throw new BusinessRuleException("DUTY_BLOCKED", reason);
        });

        Vehicle vehicle = vehicleRepository.lockById(request.vehicleId())
                .orElseThrow(() -> new NotFoundException("Vehicle", request.vehicleId()));
        if (!driver.mayDrive(vehicle)) {
            throw new BusinessRuleException("VEHICLE_NOT_ALLOWED", vehicle.getCode() + " belongs to "
                    + (vehicle.getVendor() == null ? "the LHS own fleet" : vehicle.getVendor().getName())
                    + ". Pick a vehicle from your own fleet.");
        }
        if (vehicle.getStatus() == VehicleStatus.OFF_ROAD) {
            throw new BusinessRuleException("VEHICLE_OFF_ROAD", vehicle.getCode() + " is off-road. Pick another vehicle.");
        }
        complianceService.vehicleBlockReason(vehicle).ifPresent(reason -> {
            throw new BusinessRuleException("DUTY_BLOCKED", reason);
        });
        dutyRepository.findActiveByVehicleId(vehicle.getId()).ifPresent(other -> {
            throw new BusinessRuleException("VEHICLE_IN_USE", vehicle.getCode() + " is already on duty with another driver.");
        });

        Set<String> ticked = request.checklist().stream()
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> missing = DriverDtos.REQUIRED_CHECKS.stream().filter(c -> !ticked.contains(c)).toList();
        if (!missing.isEmpty()) {
            throw new InvalidRequestException("CHECKLIST_INCOMPLETE", "Complete the vehicle check: "
                    + missing.stream().map(CHECK_LABELS::get).collect(Collectors.joining(", ")) + ".");
        }
        if (request.startOdometer() < vehicle.getOdometerKm()) {
            throw new InvalidRequestException("ODOMETER_BELOW_LAST", "The reading " + request.startOdometer()
                    + " km is below the last recorded " + vehicle.getOdometerKm() + " km for " + vehicle.getCode() + ".");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Duty duty = new Duty();
        duty.setDriver(driver);
        duty.setVehicle(vehicle);
        duty.setStatus(DutyStatus.ACTIVE);
        duty.setStartedAt(now);
        duty.setStartOdometer(request.startOdometer());
        duty.setChecklist(String.join(",", ticked));
        dutyRepository.saveAndFlush(duty);
        vehicle.setOdometerKm(request.startOdometer());

        auditService.record(user, "DUTY_STARTED", "VEHICLE", vehicle.getId(),
                driver.getDriverCode() + " signed on with " + vehicle.getCode() + " at " + request.startOdometer() + " km");
        events.publishEvent(new DispatchRequestedEvent(driver.getDriverCode() + " signed on with " + vehicle.getCode()));
        return toDto(duty, driver);
    }

    @Transactional
    public DutySummaryDto end(AuthenticatedUser user, EndDutyRequest request) {
        Driver driver = driverContext.require(user);
        Duty duty = dutyRepository.findActiveByDriverId(driver.getId())
                .orElseThrow(() -> new BusinessRuleException("NOT_ON_DUTY", "You are not signed on."));
        Vehicle vehicle = duty.getVehicle();
        tripRepository.findOpenByVehicleId(vehicle.getId()).ifPresent(trip -> {
            throw new BusinessRuleException("TRIP_OPEN", "Finish trip " + trip.getTripCode() + " before signing off.");
        });
        if (request.endOdometer() < duty.getStartOdometer()) {
            throw new InvalidRequestException("ODOMETER_BELOW_START", "The closing reading " + request.endOdometer()
                    + " km is below the opening " + duty.getStartOdometer() + " km.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        duty.setStatus(DutyStatus.CLOSED);
        duty.setEndedAt(now);
        duty.setEndOdometer(request.endOdometer());
        if (request.fuelLitres() != null) {
            BigDecimal previous = duty.getFuelLitres() == null ? BigDecimal.ZERO : duty.getFuelLitres();
            duty.setFuelLitres(previous.add(request.fuelLitres()));
        }
        vehicle.setOdometerKm(request.endOdometer());

        List<Trip> trips = tripRepository.findByDutyIdAndStatus(duty.getId(), TripStatus.COMPLETED);
        BigDecimal gpsKm = trips.stream().map(t -> t.getDistanceKm() == null ? BigDecimal.ZERO : t.getDistanceKm())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int riders = (int) bookingRepository.sumCompletedSeatsForDuty(duty.getId());
        int odometerKm = request.endOdometer() - duty.getStartOdometer();
        // The odometer also counts runs between trips, so it may exceed GPS trip km, but never undercut it.
        double gps = gpsKm.doubleValue();
        String check = odometerKm + 1 < gps || odometerKm > gps * 1.5 + 10 ? "REVIEW" : "MATCHES";

        auditService.record(user, "DUTY_ENDED", "VEHICLE", vehicle.getId(), driver.getDriverCode() + " signed off "
                + vehicle.getCode() + " · " + trips.size() + " trips · " + gpsKm + " GPS km · odometer " + odometerKm + " km");
        return new DutySummaryDto(duty.getId(), vehicle.getCode(), duty.getStartedAt(), now, duty.minutesOnDuty(now),
                trips.size(), riders, Money.km(gpsKm), odometerKm, check, duty.getFuelLitres());
    }

    DutyDto toDto(Duty duty, Driver driver) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Trip> trips = tripRepository.findByDutyIdAndStatus(duty.getId(), TripStatus.COMPLETED);
        BigDecimal km = trips.stream().map(t -> t.getDistanceKm() == null ? BigDecimal.ZERO : t.getDistanceKm())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int riders = duty.getId() == null ? 0 : (int) bookingRepository.sumCompletedSeatsForDuty(duty.getId());
        return new DutyDto(duty.getId(), duty.getStatus(), VehicleRef.of(duty.getVehicle()), duty.getStartedAt(),
                duty.minutesOnDuty(now), driver.getMaxDutyMinutes(), duty.getStartOdometer(), trips.size(), riders,
                Money.km(km));
    }
}
