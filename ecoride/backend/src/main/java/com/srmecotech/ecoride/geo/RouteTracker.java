package com.srmecotech.ecoride.geo;

import java.util.List;

/**
 * Where a moving cab is along a planned route. The live "2.4 km · 7 min away" on the
 * rider's screen comes from here: the cab's GPS fix is snapped to the nearest segment of
 * the route, and the remaining distance is measured along the road, not as the crow flies.
 */
public final class RouteTracker {

    private final List<GeoPoint> points;
    private final double[] cumulative;

    public RouteTracker(List<GeoPoint> points) {
        if (points.isEmpty()) {
            throw new IllegalArgumentException("A route needs at least one point");
        }
        this.points = points;
        this.cumulative = new double[points.size()];
        for (int i = 1; i < points.size(); i++) {
            cumulative[i] = cumulative[i - 1] + GeoMath.distanceM(points.get(i - 1), points.get(i));
        }
    }

    public static RouteTracker of(String encodedPolyline) {
        return new RouteTracker(Polyline.decode(encodedPolyline));
    }

    public double totalM() {
        return cumulative[cumulative.length - 1];
    }

    public List<GeoPoint> points() {
        return points;
    }

    /**
     * @param position the cab's current fix
     * @return how far along the route it is, how much is left, and how far it is from the route
     */
    public Progress locate(GeoPoint position) {
        if (points.size() == 1) {
            double d = GeoMath.distanceM(points.get(0), position);
            return new Progress(0, 0, d, points.get(0));
        }
        double bestOffRoute = Double.MAX_VALUE;
        double bestAlong = 0;
        GeoPoint bestSnap = points.get(0);
        for (int i = 0; i < points.size() - 1; i++) {
            GeoPoint a = points.get(i);
            GeoPoint b = points.get(i + 1);
            double t = projection(a, b, position);
            GeoPoint snap = GeoMath.interpolate(a, b, t);
            double off = GeoMath.distanceM(snap, position);
            if (off < bestOffRoute) {
                bestOffRoute = off;
                bestAlong = cumulative[i] + (cumulative[i + 1] - cumulative[i]) * t;
                bestSnap = snap;
            }
        }
        return new Progress(bestAlong, Math.max(0, totalM() - bestAlong), bestOffRoute, bestSnap);
    }

    /** The point a given distance along the route (simulated drivers drive with this). */
    public GeoPoint pointAt(double alongM) {
        if (alongM <= 0) {
            return points.get(0);
        }
        if (alongM >= totalM()) {
            return points.get(points.size() - 1);
        }
        int lo = 0;
        int hi = cumulative.length - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (cumulative[mid] <= alongM) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        double segment = cumulative[hi] - cumulative[lo];
        double t = segment <= 0 ? 0 : (alongM - cumulative[lo]) / segment;
        return GeoMath.interpolate(points.get(lo), points.get(hi), t);
    }

    /** Heading of the route at a distance along it. */
    public int headingAt(double alongM) {
        GeoPoint here = pointAt(Math.max(0, alongM - 5));
        GeoPoint ahead = pointAt(Math.min(totalM(), alongM + 15));
        return GeoMath.bearing(here, ahead);
    }

    /** Fraction (clamped 0..1) along segment a-b of the point closest to p, on a local flat projection. */
    private static double projection(GeoPoint a, GeoPoint b, GeoPoint p) {
        double cos = Math.cos(Math.toRadians(a.lat()));
        double ax = a.lng() * cos;
        double ay = a.lat();
        double bx = b.lng() * cos;
        double by = b.lat();
        double px = p.lng() * cos;
        double py = p.lat();
        double dx = bx - ax;
        double dy = by - ay;
        double len2 = dx * dx + dy * dy;
        if (len2 == 0) {
            return 0;
        }
        double t = ((px - ax) * dx + (py - ay) * dy) / len2;
        return Math.max(0, Math.min(1, t));
    }

    /**
     * @param alongM     metres from the start of the route to the snapped position
     * @param remainingM metres left to the end of the route
     * @param offRouteM  metres between the fix and the route (large = the cab left the planned route)
     */
    public record Progress(double alongM, double remainingM, double offRouteM, GeoPoint snapped) {
    }
}
