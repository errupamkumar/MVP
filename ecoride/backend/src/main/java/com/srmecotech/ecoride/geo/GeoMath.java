package com.srmecotech.ecoride.geo;

public final class GeoMath {

    public static final double EARTH_RADIUS_M = 6_371_000d;

    private GeoMath() {
    }

    /** Great-circle (haversine) distance in metres. */
    public static double distanceM(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_M * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public static double distanceM(GeoPoint a, GeoPoint b) {
        return distanceM(a.lat(), a.lng(), b.lat(), b.lng());
    }

    /** Initial bearing from a to b, degrees clockwise from north (0-359). */
    public static int bearing(GeoPoint a, GeoPoint b) {
        double phi1 = Math.toRadians(a.lat());
        double phi2 = Math.toRadians(b.lat());
        double dLng = Math.toRadians(b.lng() - a.lng());
        double y = Math.sin(dLng) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(dLng);
        int deg = (int) Math.round(Math.toDegrees(Math.atan2(y, x)));
        return (deg + 360) % 360;
    }

    /** The point a fraction t (0..1) of the way from a to b; fine for the short segments of a road route. */
    public static GeoPoint interpolate(GeoPoint a, GeoPoint b, double t) {
        return new GeoPoint(a.lat() + (b.lat() - a.lat()) * t, a.lng() + (b.lng() - a.lng()) * t);
    }

    /** The point at a distance and bearing from an origin. */
    public static GeoPoint offset(GeoPoint origin, double distanceM, double bearingDeg) {
        double delta = distanceM / EARTH_RADIUS_M;
        double theta = Math.toRadians(bearingDeg);
        double phi1 = Math.toRadians(origin.lat());
        double lambda1 = Math.toRadians(origin.lng());
        double phi2 = Math.asin(Math.sin(phi1) * Math.cos(delta) + Math.cos(phi1) * Math.sin(delta) * Math.cos(theta));
        double lambda2 = lambda1 + Math.atan2(Math.sin(theta) * Math.sin(delta) * Math.cos(phi1),
                Math.cos(delta) - Math.sin(phi1) * Math.sin(phi2));
        return new GeoPoint(Math.toDegrees(phi2), Math.toDegrees(lambda2));
    }

    /** Degrees of latitude spanning the given metres (for bounding-box prefilters). */
    public static double latDelta(double metres) {
        return Math.toDegrees(metres / EARTH_RADIUS_M);
    }

    /** Degrees of longitude spanning the given metres at a latitude. */
    public static double lngDelta(double metres, double atLat) {
        return Math.toDegrees(metres / (EARTH_RADIUS_M * Math.cos(Math.toRadians(atLat))));
    }
}
