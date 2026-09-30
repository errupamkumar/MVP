package com.srmecotech.plantride.masterdata;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;

/**
 * Read-only view of a route's configured stop sequence with the arithmetic
 * every module needs: minutes and kilometres between two positions, and
 * where a stop sits in the sequence.
 *
 * <p>Positions ("seq") are the route's own ordering. A loop lists its first
 * stop again at the end, so one stop can occupy two positions.
 */
public final class RoutePlan {

    private final Route route;
    private final List<RouteStop> stops;

    private RoutePlan(Route route, List<RouteStop> stops) {
        this.route = route;
        this.stops = stops;
    }

    /** The route's stops collection must already be initialised (fetch-joined). */
    public static RoutePlan of(Route route) {
        List<RouteStop> ordered = new ArrayList<>(route.getStops());
        ordered.sort(Comparator.comparingInt(RouteStop::getSeq));
        return new RoutePlan(route, List.copyOf(ordered));
    }

    public Route route() {
        return route;
    }

    public List<RouteStop> stops() {
        return stops;
    }

    public boolean hasSeq(int seq) {
        return stops.stream().anyMatch(s -> s.getSeq() == seq);
    }

    public RouteStop at(int seq) {
        return stops.stream()
                .filter(s -> s.getSeq() == seq)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Route " + route.getCode() + " has no stop at position " + seq));
    }

    public Stop stopAt(int seq) {
        return at(seq).getStop();
    }

    public int lastSeq() {
        return stops.isEmpty() ? 0 : stops.get(stops.size() - 1).getSeq();
    }

    /** Travel minutes from position {@code fromSeq} to {@code toSeq} (0 if toSeq <= fromSeq). */
    public int minutesBetween(int fromSeq, int toSeq) {
        int total = 0;
        for (RouteStop s : stops) {
            if (s.getSeq() > fromSeq && s.getSeq() <= toSeq) {
                total += s.getLegMinutes();
            }
        }
        return total;
    }

    /** Road kilometres from position {@code fromSeq} to {@code toSeq}. */
    public BigDecimal kmBetween(int fromSeq, int toSeq) {
        BigDecimal total = BigDecimal.ZERO;
        for (RouteStop s : stops) {
            if (s.getSeq() > fromSeq && s.getSeq() <= toSeq) {
                total = total.add(s.getLegKm());
            }
        }
        return total;
    }

    public int totalMinutes() {
        return stops.isEmpty() ? 0 : minutesBetween(stops.get(0).getSeq(), lastSeq());
    }

    public BigDecimal totalKm() {
        return stops.isEmpty() ? BigDecimal.ZERO : kmBetween(stops.get(0).getSeq(), lastSeq());
    }

    public List<Integer> seqsOfStop(long stopId) {
        return stops.stream()
                .filter(s -> s.getStop().getId() == stopId)
                .map(RouteStop::getSeq)
                .toList();
    }

    /** The latest position of {@code stopId} at or before {@code seq}; used to place an idle cab on the route. */
    public OptionalInt lastSeqOfStopAtOrBefore(long stopId, int seq) {
        return stops.stream()
                .filter(s -> s.getStop().getId() == stopId && s.getSeq() <= seq)
                .mapToInt(RouteStop::getSeq)
                .max();
    }
}
