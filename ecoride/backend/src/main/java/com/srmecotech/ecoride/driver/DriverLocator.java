package com.srmecotech.ecoride.driver;

import com.srmecotech.ecoride.geo.GeoMath;
import com.srmecotech.ecoride.geo.GeoPoint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Finds free drivers near a point: a bounding-box prefilter in SQL (indexed on availability
 * and vehicle type), then exact great-circle distance in Java.
 */
@Component
public class DriverLocator {

    private final JdbcTemplate jdbc;

    public DriverLocator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param vehicleType   null for every type
     * @param freshSince    ignore drivers whose last fix is older than this
     * @param simulatedOnly null = real and simulated, true = simulated only
     * @param excludeOffersForRide drivers already offered this ride (any answer) are skipped; null for none
     * @param freeOfOffers  skip drivers holding an unanswered offer for another ride
     */
    public List<NearbyDriver> idleNear(GeoPoint point, double radiusM, String vehicleType, Instant freshSince,
                                       Boolean simulatedOnly, Long excludeOffersForRide, boolean freeOfOffers) {
        double dLat = GeoMath.latDelta(radiusM);
        double dLng = GeoMath.lngDelta(radiusM, point.lat());
        StringBuilder sql = new StringBuilder("""
                SELECT d.id, d.lat, d.lng, d.heading, d.vehicle_type, d.simulated
                FROM driver d
                WHERE d.availability = 'ONLINE' AND d.status = 'APPROVED'
                  AND d.located_at >= ?
                  AND d.lat BETWEEN ? AND ? AND d.lng BETWEEN ? AND ?
                """);
        List<Object> args = new ArrayList<>(List.of(Timestamp.from(freshSince),
                point.lat() - dLat, point.lat() + dLat, point.lng() - dLng, point.lng() + dLng));
        if (vehicleType != null) {
            sql.append(" AND d.vehicle_type = ?");
            args.add(vehicleType);
        }
        if (Boolean.TRUE.equals(simulatedOnly)) {
            sql.append(" AND d.simulated = TRUE");
        }
        if (excludeOffersForRide != null) {
            sql.append(" AND d.id NOT IN (SELECT o.driver_id FROM ride_offer o WHERE o.ride_id = ?)");
            args.add(excludeOffersForRide);
        }
        if (freeOfOffers) {
            sql.append(" AND NOT EXISTS (SELECT 1 FROM ride_offer p WHERE p.driver_id = d.id AND p.status = 'PENDING')");
        }
        List<NearbyDriver> found = jdbc.query(sql.toString(), (rs, i) -> {
            double lat = rs.getDouble(2);
            double lng = rs.getDouble(3);
            Integer heading = (Integer) rs.getObject(4);
            return new NearbyDriver(rs.getLong(1), lat, lng, heading, rs.getString(5), rs.getBoolean(6),
                    GeoMath.distanceM(point.lat(), point.lng(), lat, lng));
        }, args.toArray());
        return found.stream()
                .filter(d -> d.distanceM() <= radiusM)
                .sorted(Comparator.comparingDouble(NearbyDriver::distanceM))
                .toList();
    }

    public record NearbyDriver(long id, double lat, double lng, Integer heading, String vehicleType, boolean simulated,
                               double distanceM) {
    }
}
