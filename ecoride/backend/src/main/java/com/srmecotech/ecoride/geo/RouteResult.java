package com.srmecotech.ecoride.geo;

/**
 * A driving route between two points.
 *
 * @param distanceM road distance
 * @param durationS expected driving time, already scaled for city traffic
 * @param polyline  encoded polyline (precision 5)
 * @param provider  OSRM (road network) or STRAIGHT (fallback estimate)
 */
public record RouteResult(int distanceM, int durationS, String polyline, String provider) {

    public boolean isRoad() {
        return "OSRM".equals(provider);
    }
}
