package com.srmecotech.ecoride.geo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Responses from the online map providers, kept so a repeated route or search costs nothing. */
@Component
public class GeoCache {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public GeoCache(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public Optional<String> get(String key, Duration maxAge) {
        List<String> rows = jdbc.queryForList(
                "SELECT payload FROM geo_cache WHERE cache_key = ? AND created_at >= ?", String.class,
                key, Timestamp.from(clock.instant().minus(maxAge)));
        return rows.stream().findFirst();
    }

    public void put(String key, String payload) {
        jdbc.update("""
                INSERT INTO geo_cache (cache_key, payload, created_at) VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE payload = VALUES(payload), created_at = VALUES(created_at)
                """, key, payload, Timestamp.from(clock.instant()));
    }
}
