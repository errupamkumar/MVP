package com.srmecotech.plantride.gps;

import com.srmecotech.plantride.booking.Booking;
import com.srmecotech.plantride.booking.BookingRepository;
import com.srmecotech.plantride.booking.BookingStatus;
import com.srmecotech.plantride.common.error.BusinessRuleException;
import com.srmecotech.plantride.common.security.AuthenticatedUser;
import com.srmecotech.plantride.common.util.GeoMath;
import com.srmecotech.plantride.masterdata.Driver;
import com.srmecotech.plantride.masterdata.RoutePlan;
import com.srmecotech.plantride.masterdata.RouteResolver;
import com.srmecotech.plantride.masterdata.Stop;
import com.srmecotech.plantride.masterdata.Vehicle;
import com.srmecotech.plantride.masterdata.VehicleRepository;
import com.srmecotech.plantride.safety.AlertService;
import com.srmecotech.plantride.safety.AlertSeverity;
import com.srmecotech.plantride.safety.AlertType;
import com.srmecotech.plantride.trip.DriverContext;
import com.srmecotech.plantride.trip.Duty;
import com.srmecotech.plantride.trip.DutyRepository;
import com.srmecotech.plantride.trip.Trip;
import com.srmecotech.plantride.trip.TripItinerary;
import com.srmecotech.plantride.trip.TripRepository;
import com.srmecotech.plantride.trip.TripService;
import com.srmecotech.plantride.trip.dto.DriverDtos.LocationAckDto;
import com.srmecotech.plantride.trip.dto.DriverDtos.LocationPingRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * GPS ingest from the driver app (AIS-140 devices post the same shape).
 * Tracks the vehicle, never the person; moves the cab its riders see on
 * their screens; raises overspeed alerts; and when a ping lands inside the
 * next stop's geofence, records the arrival exactly as if the driver had
 * tapped "Arrived", which is how a drop closes a trip. Fixes outside the
 * plant's service area are ignored.
 */
@Service
public class GpsService {

    /** Speed tolerance before an overspeed alert (GPS speed jitter). */
    private static final double OVERSPEED_TOLERANCE_KMH = 2.0;
    /** Plant-wide default when the cab is not on a trip. */
    private static final int DEFAULT_SPEED_LIMIT_KMH = 20;

    private final DriverContext driverContext;
    private final DutyRepository dutyRepository;
    private final TripRepository tripRepository;
    private final BookingRepository bookingRepository;
    private final GpsPingRepository pingRepository;
    private final VehicleRepository vehicleRepository;
    private final LiveLocationService liveLocations;
    private final RouteResolver routeResolver;
    private final TripService tripService;
    private final AlertService alertService;
    private final Clock clock;

    public GpsService(DriverContext driverContext, DutyRepository dutyRepository, TripRepository tripRepository,
                      BookingRepository bookingRepository, GpsPingRepository pingRepository,
                      VehicleRepository vehicleRepository, LiveLocationService liveLocations,
                      RouteResolver routeResolver, TripService tripService, AlertService alertService, Clock clock) {
        this.driverContext = driverContext;
        this.dutyRepository = dutyRepository;
        this.tripRepository = tripRepository;
        this.bookingRepository = bookingRepository;
        this.pingRepository = pingRepository;
        this.vehicleRepository = vehicleRepository;
        this.liveLocations = liveLocations;
        this.routeResolver = routeResolver;
        this.tripService = tripService;
        this.alertService = alertService;
        this.clock = clock;
    }

    @Transactional
    public LocationAckDto ingest(AuthenticatedUser user, LocationPingRequest request) {
        Driver driver = driverContext.require(user);
        Duty duty = dutyRepository.findActiveByDriverId(driver.getId())
                .orElseThrow(() -> new BusinessRuleException("NOT_ON_DUTY", "Location is only shared while on duty."));
        Vehicle vehicle = duty.getVehicle();
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime recordedAt = recordedAt(request, now);
        double speed = request.speedKmh() == null ? 0 : request.speedKmh();
        Trip trip = tripRepository.findOpenByVehicleId(vehicle.getId()).orElse(null);
        int limit = trip != null ? trip.getRoute().getSpeedLimitKmh() : DEFAULT_SPEED_LIMIT_KMH;
        if (!liveLocations.insideServiceArea(request.latitude(), request.longitude())) {
            return new LocationAckDto(false, limit, null, true);
        }

        // Move the cab before inserting the ping: the insert's foreign-key check takes a shared
        // lock on the vehicle row, and upgrading that to the update's exclusive lock can deadlock.
        boolean newest = vehicleRepository.moveTo(vehicle.getId(), request.latitude(), request.longitude(), speed,
                request.accuracyM(), request.heading(), recordedAt) == 1;

        GpsPing ping = new GpsPing();
        ping.setVehicle(vehicle);
        ping.setTrip(trip);
        ping.setLatitude(request.latitude());
        ping.setLongitude(request.longitude());
        ping.setSpeedKmh(speed);
        ping.setRecordedAt(recordedAt);
        ping.setReceivedAt(now);
        pingRepository.save(ping);

        // Offline buffers flush out of order: only the newest, recent point alerts or arrives.
        boolean live = newest && !recordedAt.isBefore(now.minusSeconds(LiveLocationService.LIVE_SECONDS));

        boolean overspeed = speed > limit + OVERSPEED_TOLERANCE_KMH;
        if (overspeed && live) {
            String near = vehicle.getCurrentStop() == null ? "" : " near " + vehicle.getCurrentStop().getName();
            alertService.raise(AlertType.OVERSPEED, AlertSeverity.HIGH, vehicle.getCode() + " overspeed · "
                            + Math.round(speed) + " km/h in a " + limit + " km/h zone" + near + " · driver warned by voice")
                    .vehicle(vehicle)
                    .trip(trip)
                    .at(request.latitude(), request.longitude())
                    .dedupe("OVERSPEED:" + vehicle.getId(), 5)
                    .save();
        }

        String autoArrivedAt = null;
        if (trip != null && live) {
            autoArrivedAt = tryGeofenceArrival(trip, request.latitude(), request.longitude());
        }
        return new LocationAckDto(overspeed, limit, autoArrivedAt, false);
    }

    /** Device age wins over a device timestamp (phone clocks and time zones drift); a future time means now. */
    private static LocalDateTime recordedAt(LocationPingRequest request, LocalDateTime now) {
        if (request.ageSeconds() != null) {
            return now.minusSeconds(request.ageSeconds());
        }
        return request.recordedAt() == null || request.recordedAt().isAfter(now.plusMinutes(2)) ? now : request.recordedAt();
    }

    private String tryGeofenceArrival(Trip trip, double lat, double lng) {
        RoutePlan plan = routeResolver.planFor(trip.getRoute().getId());
        List<Booking> active = bookingRepository.findOnTrip(trip.getId(), BookingStatus.ON_TRIP);
        // Check first instead of catching: an exception thrown through the
        // transactional arriveAt() would mark this whole transaction rollback-only.
        if (TripItinerary.arrivalBlocker(trip, plan, active).isPresent()) {
            return null;
        }
        Integer next = TripItinerary.nextStoppingSeq(trip, active);
        if (next == null) {
            return null;
        }
        Stop stop = plan.stopAt(next);
        double metres = GeoMath.distanceMetres(lat, lng, stop.getLatitude(), stop.getLongitude());
        if (metres > stop.getGeofenceM()) {
            return null;
        }
        tripService.arriveAt(trip, plan, next);
        return stop.getName();
    }
}
