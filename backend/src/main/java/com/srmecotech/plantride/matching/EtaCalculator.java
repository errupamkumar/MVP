package com.srmecotech.plantride.matching;

import com.srmecotech.plantride.common.config.AppProperties;
import com.srmecotech.plantride.common.util.GeoMath;
import com.srmecotech.plantride.gps.LiveLocationService;
import com.srmecotech.plantride.masterdata.RoutePlan;
import com.srmecotech.plantride.masterdata.Stop;
import com.srmecotech.plantride.masterdata.Vehicle;
import com.srmecotech.plantride.trip.Trip;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.OptionalInt;

/**
 * Phase-1 travel-time model: configured leg times along the route, a
 * straight-line estimate (with a road factor) for a cab that is off the
 * route, and a fixed dwell for every intermediate stop that has a pickup or
 * drop. A cab starts from the last stop it confirmed, unless its driver's
 * phone shows it has driven away from there. Phase 2 replaces the leg table
 * with learned in-plant ETAs.
 */
@Component
public class EtaCalculator {

    /** A live GPS fix this far from the cab's last confirmed stop means it has left that stop. */
    private static final int LEFT_STOP_METRES = 150;

    private final AppProperties.Matching config;
    private final Clock clock;

    public EtaCalculator(AppProperties properties, Clock clock) {
        this.config = properties.matching();
        this.clock = clock;
    }

    public record Reposition(int minutes, double km) {
    }

    /** How far an idle cab is from route position {@code targetSeq}. */
    public Reposition reposition(Vehicle vehicle, RoutePlan plan, int targetSeq) {
        Stop at = vehicle.getCurrentStop();
        if (at != null && !leftStop(vehicle, at)) {
            return fromStop(at, plan, targetSeq);
        }
        if (vehicle.getLastLatitude() != null && vehicle.getLastLongitude() != null) {
            return straightLine(vehicle.getLastLatitude(), vehicle.getLastLongitude(), plan.stopAt(targetSeq));
        }
        // Position unknown: assume a cab from the far side of the plant.
        return new Reposition(10, 3.0);
    }

    /** Travel from a stop to route position {@code targetSeq}: along the route if the stop is on it before the target, else across. */
    public Reposition fromStop(Stop at, RoutePlan plan, int targetSeq) {
        OptionalInt onRoute = plan.lastSeqOfStopAtOrBefore(at.getId(), targetSeq);
        if (onRoute.isPresent()) {
            return new Reposition(plan.minutesBetween(onRoute.getAsInt(), targetSeq),
                    plan.kmBetween(onRoute.getAsInt(), targetSeq).doubleValue());
        }
        return straightLine(at.getLatitude(), at.getLongitude(), plan.stopAt(targetSeq));
    }

    /** Estimated road kilometres from a vehicle's last known position to a stop, or null if the position is unknown. */
    public Double roadKmTo(Vehicle vehicle, Stop target) {
        Stop at = vehicle.getCurrentStop();
        if (at != null && !leftStop(vehicle, at)) {
            return straightLine(at.getLatitude(), at.getLongitude(), target).km();
        }
        if (vehicle.getLastLatitude() != null && vehicle.getLastLongitude() != null) {
            return straightLine(vehicle.getLastLatitude(), vehicle.getLastLongitude(), target).km();
        }
        return null;
    }

    /**
     * Minutes until an open trip's cab reaches route position {@code targetSeq}.
     *
     * @param stoppingSeqs positions where the cab stops for a pickup or drop
     */
    public int minutesUntil(Trip trip, RoutePlan plan, int targetSeq, Collection<Integer> stoppingSeqs) {
        LocalDateTime now = LocalDateTime.now(clock);
        Integer current = trip.getCurrentSeq();
        if (current == null) {
            Vehicle vehicle = trip.getVehicle();
            int toStart = reposition(vehicle, plan, trip.getStartSeq()).minutes();
            // A live position already reflects the driving done since the trip was created; a stop does not.
            boolean fromLivePosition = liveGps(vehicle)
                    && (vehicle.getCurrentStop() == null || leftStop(vehicle, vehicle.getCurrentStop()));
            int elapsed = fromLivePosition ? 0 : minutesSince(trip.getCreatedAt(), now);
            int remainingToStart = toStart == 0 ? 0 : Math.max(1, toStart - elapsed);
            if (targetSeq <= trip.getStartSeq()) {
                return remainingToStart;
            }
            return remainingToStart + plan.minutesBetween(trip.getStartSeq(), targetSeq)
                    + dwell(stoppingSeqs, trip.getStartSeq() - 1, targetSeq);
        }
        if (targetSeq <= current) {
            return 0;
        }
        int travel = plan.minutesBetween(current, targetSeq);
        int elapsed = minutesSince(trip.getLastArrivedAt(), now);
        return Math.max(1, travel - elapsed) + dwell(stoppingSeqs, current, targetSeq);
    }

    /** The driver's phone reports the cab well away from {@code stop}; a stale fix never counts. */
    private boolean leftStop(Vehicle vehicle, Stop stop) {
        return liveGps(vehicle) && GeoMath.distanceMetres(vehicle.getLastLatitude(), vehicle.getLastLongitude(),
                stop.getLatitude(), stop.getLongitude()) > Math.max(LEFT_STOP_METRES, 2 * stop.getGeofenceM());
    }

    private boolean liveGps(Vehicle vehicle) {
        return vehicle.getLastPingAt() != null && vehicle.getLastLatitude() != null && vehicle.getLastLongitude() != null
                && !vehicle.getLastPingAt().isBefore(LocalDateTime.now(clock).minusSeconds(LiveLocationService.LIVE_SECONDS));
    }

    private int dwell(Collection<Integer> stoppingSeqs, int afterSeq, int beforeSeq) {
        long stops = stoppingSeqs.stream().filter(s -> s > afterSeq && s < beforeSeq).distinct().count();
        return (int) stops * config.dwellMinutes();
    }

    private Reposition straightLine(double lat, double lng, Stop target) {
        double km = GeoMath.distanceKm(lat, lng, target.getLatitude(), target.getLongitude()) * config.roadFactor();
        int minutes = (int) Math.ceil(km / config.averageSpeedKmh() * 60d) + 1;
        return new Reposition(minutes, Math.round(km * 100d) / 100d);
    }

    private static int minutesSince(LocalDateTime then, LocalDateTime now) {
        if (then == null) {
            return 0;
        }
        return (int) Math.max(0, Duration.between(then, now).toMinutes());
    }
}
