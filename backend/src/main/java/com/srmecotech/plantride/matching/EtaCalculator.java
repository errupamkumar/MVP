package com.srmecotech.plantride.matching;

import com.srmecotech.plantride.common.config.AppProperties;
import com.srmecotech.plantride.common.util.GeoMath;
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
 * drop. Phase 2 replaces the leg table with learned in-plant ETAs.
 */
@Component
public class EtaCalculator {

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
        Stop target = plan.stopAt(targetSeq);
        Stop at = vehicle.getCurrentStop();
        if (at != null) {
            OptionalInt onRoute = plan.lastSeqOfStopAtOrBefore(at.getId(), targetSeq);
            if (onRoute.isPresent()) {
                return new Reposition(plan.minutesBetween(onRoute.getAsInt(), targetSeq),
                        plan.kmBetween(onRoute.getAsInt(), targetSeq).doubleValue());
            }
            return straightLine(at.getLatitude(), at.getLongitude(), target);
        }
        if (vehicle.getLastLatitude() != null && vehicle.getLastLongitude() != null) {
            return straightLine(vehicle.getLastLatitude(), vehicle.getLastLongitude(), target);
        }
        // Position unknown: assume a cab from the far side of the plant.
        return new Reposition(10, 3.0);
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
            int toStart = reposition(trip.getVehicle(), plan, trip.getStartSeq()).minutes();
            int elapsed = minutesSince(trip.getCreatedAt(), now);
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
