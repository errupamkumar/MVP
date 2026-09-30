package com.srmecotech.plantride.common.util;

public final class GeoMath {

    private static final double EARTH_RADIUS_M = 6_371_000d;

    private GeoMath() {
    }

    /** Great-circle distance in metres. */
    public static double distanceMetres(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_M * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        return distanceMetres(lat1, lon1, lat2, lon2) / 1000d;
    }
}
