package com.srmecotech.ecoride.ride;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Append-only ride history: the timeline (events) and the GPS trace (track points). */
@Component
public class RideLog {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public RideLog(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public void event(long rideId, String type, String actor, String message) {
        jdbc.update("INSERT INTO ride_event (ride_id, type, actor, message, created_at) VALUES (?, ?, ?, ?, ?)",
                rideId, type, actor, message.length() > 255 ? message.substring(0, 255) : message,
                Timestamp.from(clock.instant()));
    }

    public List<Event> events(long rideId) {
        return jdbc.query("SELECT type, actor, message, created_at FROM ride_event WHERE ride_id = ? ORDER BY id",
                (rs, i) -> new Event(rs.getString(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant()),
                rideId);
    }

    public void track(long rideId, String phase, double lat, double lng, Double speedKmh) {
        jdbc.update("INSERT INTO ride_track_point (ride_id, phase, lat, lng, speed_kmh, recorded_at) VALUES (?, ?, ?, ?, ?, ?)",
                rideId, phase, lat, lng, speedKmh, Timestamp.from(clock.instant()));
    }

    /** The trace, thinned to at most {@code maxPoints} evenly spaced fixes (the last fix is always kept). */
    public List<TrackPoint> trace(long rideId, int maxPoints) {
        List<TrackPoint> all = jdbc.query(
                "SELECT phase, lat, lng, speed_kmh, recorded_at FROM ride_track_point WHERE ride_id = ? ORDER BY id",
                (rs, i) -> new TrackPoint(rs.getString(1), rs.getDouble(2), rs.getDouble(3), (Double) rs.getObject(4),
                        rs.getTimestamp(5).toInstant()),
                rideId);
        if (all.size() <= maxPoints) {
            return all;
        }
        List<TrackPoint> thinned = new ArrayList<>(maxPoints);
        double step = (all.size() - 1) / (double) (maxPoints - 1);
        for (int i = 0; i < maxPoints; i++) {
            thinned.add(all.get((int) Math.round(i * step)));
        }
        return thinned;
    }

    public record Event(String type, String actor, String message, Instant at) {
    }

    public record TrackPoint(String phase, double lat, double lng, Double speedKmh, Instant at) {
    }
}
