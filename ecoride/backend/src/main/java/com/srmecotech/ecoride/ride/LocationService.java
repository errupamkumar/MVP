package com.srmecotech.ecoride.ride;

import com.srmecotech.ecoride.common.settings.Setting;
import com.srmecotech.ecoride.common.settings.SettingsService;
import com.srmecotech.ecoride.driver.DriverRepository;
import com.srmecotech.ecoride.geo.GeoMath;
import com.srmecotech.ecoride.geo.GeoPoint;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Driver GPS. Every fix moves the cab on the rider's and the admin's maps; while a ride is
 * assigned it is also added to the ride's trace and its GPS odometer:
 * <ul>
 *   <li>PICKUP phase (on the way): dead mileage, for reporting.</li>
 *   <li>TRIP phase (rider on board): the distance the final fare is checked against.</li>
 * </ul>
 * Odometer filter: fixes worse than 75 m accuracy, moves under 5 m (standing-still jitter)
 * and jumps faster than GPS_MAX_SPEED_KMH are not added. Fixes from the demo simulator are
 * exempt from the speed check, since they replay a drive faster than real time.
 * <p>
 * Lock order is ride row, then driver row: the same as accept and complete.
 */
@Service
public class LocationService {

    private static final int MAX_ACCURACY_M = 75;
    private static final double MIN_MOVE_M = 5;

    private final RideRepository rides;
    private final DriverRepository drivers;
    private final SettingsService settings;
    private final RideLog rideLog;
    private final Clock clock;

    public LocationService(RideRepository rides, DriverRepository drivers, SettingsService settings, RideLog rideLog,
                           Clock clock) {
        this.rides = rides;
        this.drivers = drivers;
        this.settings = settings;
        this.rideLog = rideLog;
        this.clock = clock;
    }

    @Transactional
    public void driverPing(long driverId, GeoPoint at, Integer heading, Double speedKmh, Integer accuracyM, boolean simulated) {
        Instant now = clock.instant();
        Integer resolvedHeading = heading;
        List<Long> active = rides.findIdsByDriverAndStatus(driverId, RideStatus.ASSIGNED);
        if (!active.isEmpty()) {
            Ride ride = rides.lockById(active.get(0)).orElse(null);
            if (ride != null && ride.getStatus().isAssigned() && Long.valueOf(driverId).equals(ride.getDriverId())) {
                Integer bearing = accumulate(ride, at, speedKmh, accuracyM, simulated, now);
                if (resolvedHeading == null) {
                    resolvedHeading = bearing;
                }
            }
        }
        drivers.updateLocation(driverId, at.lat(), at.lng(), resolvedHeading, speedKmh, now);
    }

    /** @return the bearing of this move when it was counted, for devices that send no heading */
    private Integer accumulate(Ride ride, GeoPoint at, Double speedKmh, Integer accuracyM, boolean simulated, Instant now) {
        String phase = switch (ride.getStatus()) {
            case ACCEPTED -> "PICKUP";
            case IN_PROGRESS -> "TRIP";
            default -> null;
        };
        if (phase == null) {
            return null;
        }
        Integer bearing = null;
        if (ride.getLastTrackLat() == null) {
            ride.setLastTrackLat(at.lat());
            ride.setLastTrackLng(at.lng());
            ride.setLastTrackAt(now);
        } else {
            GeoPoint last = new GeoPoint(ride.getLastTrackLat(), ride.getLastTrackLng());
            double moved = GeoMath.distanceM(last, at);
            double seconds = ride.getLastTrackAt() == null ? 1
                    : Math.max(0.5, Duration.between(ride.getLastTrackAt(), now).toMillis() / 1000d);
            double impliedKmh = moved / seconds * 3.6;
            boolean accurate = accuracyM == null || accuracyM <= MAX_ACCURACY_M;
            boolean plausible = simulated || impliedKmh <= settings.getInt(Setting.GPS_MAX_SPEED_KMH);
            if (accurate && plausible && moved >= MIN_MOVE_M) {
                int metres = (int) Math.round(moved);
                if ("TRIP".equals(phase)) {
                    ride.setActualDistanceM(ride.getActualDistanceM() + metres);
                } else {
                    ride.setPickupTravelM(ride.getPickupTravelM() + metres);
                }
                bearing = GeoMath.bearing(last, at);
                ride.setLastTrackLat(at.lat());
                ride.setLastTrackLng(at.lng());
                ride.setLastTrackAt(now);
            }
        }
        rides.save(ride);
        rideLog.track(ride.getId(), phase, at.lat(), at.lng(), speedKmh);
        return bearing;
    }
}
