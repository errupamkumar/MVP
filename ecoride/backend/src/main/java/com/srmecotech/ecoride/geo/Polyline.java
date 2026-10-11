package com.srmecotech.ecoride.geo;

import java.util.ArrayList;
import java.util.List;

/**
 * Google's encoded polyline format at precision 5, the format OSRM returns with
 * {@code geometries=polyline}. Routes travel to the apps in this form (a 10 km
 * route is about 1 KB instead of 15 KB of JSON coordinates).
 */
public final class Polyline {

    private Polyline() {
    }

    public static String encode(List<GeoPoint> points) {
        StringBuilder sb = new StringBuilder();
        long prevLat = 0;
        long prevLng = 0;
        for (GeoPoint p : points) {
            long lat = Math.round(p.lat() * 1e5);
            long lng = Math.round(p.lng() * 1e5);
            encodeValue(lat - prevLat, sb);
            encodeValue(lng - prevLng, sb);
            prevLat = lat;
            prevLng = lng;
        }
        return sb.toString();
    }

    public static List<GeoPoint> decode(String encoded) {
        List<GeoPoint> points = new ArrayList<>();
        if (encoded == null || encoded.isEmpty()) {
            return points;
        }
        int index = 0;
        long lat = 0;
        long lng = 0;
        int length = encoded.length();
        while (index < length) {
            long[] result = decodeValue(encoded, index);
            lat += result[0];
            index = (int) result[1];
            if (index >= length) {
                break;
            }
            result = decodeValue(encoded, index);
            lng += result[0];
            index = (int) result[1];
            points.add(new GeoPoint(lat / 1e5, lng / 1e5));
        }
        return points;
    }

    private static void encodeValue(long value, StringBuilder sb) {
        long v = value < 0 ? ~(value << 1) : value << 1;
        while (v >= 0x20) {
            sb.append((char) ((0x20 | (v & 0x1f)) + 63));
            v >>= 5;
        }
        sb.append((char) (v + 63));
    }

    /** @return {value, nextIndex} */
    private static long[] decodeValue(String encoded, int index) {
        long result = 0;
        int shift = 0;
        int b;
        do {
            b = encoded.charAt(index++) - 63;
            result |= (long) (b & 0x1f) << shift;
            shift += 5;
        } while (b >= 0x20 && index < encoded.length());
        long value = (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
        return new long[]{value, index};
    }
}
