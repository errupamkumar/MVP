package com.srmecotech.plantride.gps;

import com.srmecotech.plantride.booking.Booking;
import com.srmecotech.plantride.booking.BookingStatus;
import com.srmecotech.plantride.common.error.BusinessRuleException;
import com.srmecotech.plantride.common.settings.SettingsService;
import com.srmecotech.plantride.common.util.GeoMath;
import com.srmecotech.plantride.gps.dto.LiveLocationDtos.CabLocationDto;
import com.srmecotech.plantride.gps.dto.LiveLocationDtos.RiderLocationAckDto;
import com.srmecotech.plantride.gps.dto.LiveLocationDtos.RiderLocationDto;
import com.srmecotech.plantride.gps.dto.LiveLocationDtos.RiderLocationRequest;
import com.srmecotech.plantride.masterdata.RoutePlan;
import com.srmecotech.plantride.masterdata.RouteStop;
import com.srmecotech.plantride.masterdata.Stop;
import com.srmecotech.plantride.masterdata.StopRepository;
import com.srmecotech.plantride.masterdata.Vehicle;
import com.srmecotech.plantride.trip.Trip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Live positions shown across the apps: the cab on its rider's screen, and a
 * waiting rider on their driver's screen. Riders share only while their cab
 * is on its way; the driver of that cab is the only other person who sees it,
 * and the fix is deleted when they board.
 */
@Service
public class LiveLocationService {

    /** A phone that reported within this window is "live"; also the window for alerts and geofence arrivals. */
    public static final int LIVE_SECONDS = 120;
    /** A rider's fix older than this is no longer shown to the driver. */
    static final int RIDER_MAX_AGE_SECONDS = 15 * 60;
    /** How far a stop can be and still be named as where the cab is. */
    private static final int NEAR_STOP_METRES = 300;
    private static final int DEFAULT_SERVICE_AREA_M = 5000;

    private static final Logger log = LoggerFactory.getLogger(LiveLocationService.class);

    private final StopRepository stopRepository;
    private final RiderLocationRepository riderLocationRepository;
    private final SettingsService settings;
    private final Clock clock;

    public LiveLocationService(StopRepository stopRepository, RiderLocationRepository riderLocationRepository,
                               SettingsService settings, Clock clock) {
        this.stopRepository = stopRepository;
        this.riderLocationRepository = riderLocationRepository;
        this.settings = settings;
        this.clock = clock;
    }

    /**
     * True when the fix is within the admin's service radius of at least one
     * active pickup point. Anything farther is a GPS glitch or a phone outside
     * the plant (a laptop running the web app), and must not move a cab or a
     * rider on anyone's screen. A radius of 0 turns the check off.
     */
    @Transactional(readOnly = true)
    public boolean insideServiceArea(double lat, double lng) {
        int radius = settings.getInt(SettingsService.SERVICE_AREA_RADIUS_M, DEFAULT_SERVICE_AREA_M);
        if (radius <= 0) {
            return true;
        }
        List<Stop> stops = stopRepository.findByActiveTrueOrderByNameAsc();
        return stops.isEmpty() || stops.stream()
                .anyMatch(s -> GeoMath.distanceMetres(lat, lng, s.getLatitude(), s.getLongitude()) <= radius);
    }

    // ------------------------------------------------------------------ the cab

    /**
     * The cab's position for a screen, with the distance to {@code towards}.
     * Null only when the cab has never reported a position.
     */
    public CabLocationDto cab(Vehicle vehicle, RoutePlan plan, Stop towards) {
        Double lat = vehicle.getLastLatitude();
        Double lng = vehicle.getLastLongitude();
        if (lat == null || lng == null) {
            if (vehicle.getCurrentStop() == null) {
                return null;
            }
            lat = vehicle.getCurrentStop().getLatitude();
            lng = vehicle.getCurrentStop().getLongitude();
        }
        Long secondsAgo = vehicle.getLastPingAt() == null ? null : secondsSince(vehicle.getLastPingAt());
        boolean live = secondsAgo != null && secondsAgo <= LIVE_SECONDS;
        Stop near = nearestStop(lat, lng, plan.stops());
        return new CabLocationDto(
                vehicle.getCode(),
                lat,
                lng,
                live ? vehicle.getLastAccuracyM() : null,
                live ? vehicle.getLastSpeedKmh() : null,
                live ? vehicle.getLastHeading() : null,
                vehicle.getLastPingAt(),
                secondsAgo,
                live,
                near == null ? null : near.getName(),
                towards == null ? null : towards.getName(),
                towards == null ? null : metres(lat, lng, towards));
    }

    // ------------------------------------------------------------------ riders

    /**
     * Records a waiting rider's position for their driver. Only while the
     * cab is on its way (ASSIGNED): before that no driver needs it, and once
     * on board the cab's own position is the rider's.
     */
    @Transactional
    public RiderLocationAckDto shareRider(Booking booking, RiderLocationRequest request) {
        if (booking.getStatus() != BookingStatus.ASSIGNED || booking.getTrip() == null) {
            throw new BusinessRuleException("NOT_WAITING_FOR_CAB",
                    "Your location is shared only while your cab is on its way to pick you up.");
        }
        Trip trip = booking.getTrip();
        String sharedWith = trip.getDriver().getUser().getFullName() + " · " + trip.getVehicle().getCode();
        Stop pickup = booking.getFromStop();
        if (!insideServiceArea(request.latitude(), request.longitude())) {
            return new RiderLocationAckDto(false, sharedWith, pickup.getName(), null, false, true,
                    "Your phone's location is outside the plant area, so it was not shared.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        int age = request.ageSeconds() == null ? 0 : request.ageSeconds();
        riderLocationRepository.upsert(booking.getId(), request.latitude(), request.longitude(),
                request.accuracyM(), now.minusSeconds(age), now);
        int toPickup = metres(request.latitude(), request.longitude(), pickup);
        boolean atPickup = toPickup <= pickup.getGeofenceM();
        return new RiderLocationAckDto(true, sharedWith, pickup.getName(), toPickup, atPickup, false,
                atPickup ? "You are at the pickup point." : null);
    }

    /**
     * Deletes the rider's position: they turned sharing off (the driver stops
     * seeing them straight away), or they boarded and the cab's position is theirs.
     */
    @Transactional
    public void forget(Booking booking) {
        riderLocationRepository.forget(booking.getId());
    }

    /** Positions of the riders a cab is on its way to, by booking id. Riders who are not sharing are absent. */
    @Transactional(readOnly = true)
    public Map<Long, RiderLocationDto> waitingRiders(Collection<Booking> bookings, Vehicle cab, RoutePlan plan) {
        Map<Long, Booking> waiting = new HashMap<>();
        bookings.stream().filter(b -> b.getStatus() == BookingStatus.ASSIGNED).forEach(b -> waiting.put(b.getId(), b));
        if (waiting.isEmpty()) {
            return Map.of();
        }
        Map<Long, RiderLocationDto> result = new HashMap<>();
        for (RiderLocation fix : riderLocationRepository.findAllById(waiting.keySet())) {
            Booking b = waiting.get(fix.getBookingId());
            RiderLocationDto dto = toDto(fix, plan.stopAt(b.getFromSeq()), cab);
            if (dto != null) {
                result.put(b.getId(), dto);
            }
        }
        return result;
    }

    /** What the rider is sharing right now, for their own screen; null when nothing is shared. */
    @Transactional(readOnly = true)
    public RiderLocationDto sharedBy(Booking booking, RoutePlan plan) {
        if (booking.getStatus() != BookingStatus.ASSIGNED || booking.getTrip() == null) {
            return null;
        }
        return riderLocationRepository.findById(booking.getId())
                .map(fix -> toDto(fix, plan.stopAt(booking.getFromSeq()), booking.getTrip().getVehicle()))
                .orElse(null);
    }

    /** Retention: positions of riders who boarded or left, and any stale fix, are deleted every few minutes. */
    @Scheduled(initialDelay = 300_000, fixedDelay = 300_000)
    @Transactional
    public void purge() {
        int removed = riderLocationRepository.purge(LocalDateTime.now(clock).minusSeconds(RIDER_MAX_AGE_SECONDS));
        if (removed > 0) {
            log.debug("Deleted {} rider location(s) no longer needed", removed);
        }
    }

    private RiderLocationDto toDto(RiderLocation fix, Stop pickup, Vehicle cab) {
        long ago = secondsSince(fix.getRecordedAt());
        if (ago > RIDER_MAX_AGE_SECONDS) {
            return null;
        }
        int toPickup = metres(fix.getLatitude(), fix.getLongitude(), pickup);
        Integer fromCab = cab.getLastLatitude() == null || cab.getLastLongitude() == null ? null
                : (int) Math.round(GeoMath.distanceMetres(fix.getLatitude(), fix.getLongitude(),
                cab.getLastLatitude(), cab.getLastLongitude()));
        return new RiderLocationDto(fix.getLatitude(), fix.getLongitude(), fix.getAccuracyM(), fix.getRecordedAt(), ago,
                ago <= LIVE_SECONDS, pickup.getName(), toPickup, toPickup <= pickup.getGeofenceM(), fromCab);
    }

    private static Stop nearestStop(double lat, double lng, List<RouteStop> stops) {
        Stop best = null;
        double bestMetres = NEAR_STOP_METRES;
        for (RouteStop rs : stops) {
            double m = GeoMath.distanceMetres(lat, lng, rs.getStop().getLatitude(), rs.getStop().getLongitude());
            if (m <= bestMetres) {
                best = rs.getStop();
                bestMetres = m;
            }
        }
        return best;
    }

    private static int metres(double lat, double lng, Stop stop) {
        return (int) Math.round(GeoMath.distanceMetres(lat, lng, stop.getLatitude(), stop.getLongitude()));
    }

    private long secondsSince(LocalDateTime then) {
        return Math.max(0, Duration.between(then, LocalDateTime.now(clock)).toSeconds());
    }
}
