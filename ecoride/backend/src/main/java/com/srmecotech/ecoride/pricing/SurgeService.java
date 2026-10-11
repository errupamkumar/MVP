package com.srmecotech.ecoride.pricing;

import com.srmecotech.ecoride.common.settings.Setting;
import com.srmecotech.ecoride.common.settings.SettingsService;
import com.srmecotech.ecoride.driver.DriverLocator;
import com.srmecotech.ecoride.geo.GeoMath;
import com.srmecotech.ecoride.geo.GeoPoint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Dynamic pricing. Surge applies only inside an active surge zone:
 * <ul>
 *   <li>MANUAL zone: the multiplier operations set (an event, heavy rain).</li>
 *   <li>AUTO zone: ride requests in the last 10 minutes vs free drivers around the pickup.
 *       Each request beyond the free drivers adds 0.25x, rounded to 0.1x.</li>
 * </ul>
 * Always capped at SURGE_CAP (Maharashtra: 1.5x the base fare).
 */
@Service
public class SurgeService {

    private static final Duration DEMAND_WINDOW = Duration.ofMinutes(10);

    private final SurgeZoneRepository zones;
    private final SettingsService settings;
    private final DriverLocator locator;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public SurgeService(SurgeZoneRepository zones, SettingsService settings, DriverLocator locator, JdbcTemplate jdbc,
                        Clock clock) {
        this.zones = zones;
        this.settings = settings;
        this.locator = locator;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public SurgeInfo surgeAt(GeoPoint point) {
        if (!settings.getBool(Setting.SURGE_ENABLED)) {
            return SurgeInfo.none();
        }
        SurgeZone zone = zones.findByActiveTrue().stream()
                .filter(z -> GeoMath.distanceM(z.getLat(), z.getLng(), point.lat(), point.lng()) <= z.getRadiusM())
                .min(Comparator.comparingDouble(z -> GeoMath.distanceM(z.getLat(), z.getLng(), point.lat(), point.lng())))
                .orElse(null);
        if (zone == null) {
            return SurgeInfo.none();
        }
        return forZone(zone, point);
    }

    /** The multiplier a zone charges right now (measured at its centre, or at a pickup inside it). */
    public SurgeInfo forZone(SurgeZone zone, GeoPoint at) {
        BigDecimal cap = BigDecimal.valueOf(settings.getDouble(Setting.SURGE_CAP));
        if ("MANUAL".equals(zone.getMode())) {
            BigDecimal m = zone.getManualMultiplier().min(cap).setScale(2, RoundingMode.HALF_UP);
            return new SurgeInfo(m, zone.getName(), "MANUAL", null, null);
        }
        double radius = settings.getInt(Setting.SURGE_RADIUS_M);
        int demand = demandNear(at, radius);
        Instant fresh = clock.instant().minusSeconds(settings.getInt(Setting.DRIVER_LOCATION_FRESH_S));
        int supply = locator.idleNear(at, radius, null, fresh, null, null, false).size();
        double ratio = demand / (double) Math.max(1, supply);
        double raw = ratio <= 1 ? 1.0 : 1.0 + (ratio - 1) * 0.25;
        BigDecimal m = BigDecimal.valueOf(Math.round(raw * 10) / 10.0).min(cap).setScale(2, RoundingMode.HALF_UP);
        return new SurgeInfo(m, zone.getName(), "AUTO", demand, supply);
    }

    private int demandNear(GeoPoint point, double radiusM) {
        double dLat = GeoMath.latDelta(radiusM);
        double dLng = GeoMath.lngDelta(radiusM, point.lat());
        List<double[]> pickups = jdbc.query("""
                        SELECT pickup_lat, pickup_lng FROM ride
                        WHERE requested_at >= ? AND pickup_lat BETWEEN ? AND ? AND pickup_lng BETWEEN ? AND ?
                        """,
                (rs, i) -> new double[]{rs.getDouble(1), rs.getDouble(2)},
                Timestamp.from(clock.instant().minus(DEMAND_WINDOW)),
                point.lat() - dLat, point.lat() + dLat, point.lng() - dLng, point.lng() + dLng);
        return (int) pickups.stream()
                .filter(p -> GeoMath.distanceM(point.lat(), point.lng(), p[0], p[1]) <= radiusM)
                .count();
    }

    /**
     * @param demand requests in the last 10 minutes (AUTO zones only)
     * @param supply free drivers nearby (AUTO zones only)
     */
    public record SurgeInfo(BigDecimal multiplier, String zone, String mode, Integer demand, Integer supply) {

        static SurgeInfo none() {
            return new SurgeInfo(BigDecimal.ONE.setScale(2, RoundingMode.HALF_UP), null, null, null, null);
        }

        public boolean active() {
            return multiplier.compareTo(BigDecimal.ONE) > 0;
        }
    }
}
