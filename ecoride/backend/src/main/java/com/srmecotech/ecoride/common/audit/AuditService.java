package com.srmecotech.ecoride.common.audit;

import com.srmecotech.ecoride.common.security.AuthenticatedUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Who changed what from the admin console: approvals, blocks, fares, settings, refunds. */
@Service
public class AuditService {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AuditService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public void record(AuthenticatedUser actor, String action, String target, String details) {
        jdbc.update("INSERT INTO audit_log (actor, action, target, details, created_at) VALUES (?, ?, ?, ?, ?)",
                actor.name(), action, truncate(target, 80), truncate(details, 500), Timestamp.from(clock.instant()));
    }

    public List<AuditEntry> recent(int limit) {
        return jdbc.query("SELECT id, actor, action, target, details, created_at FROM audit_log ORDER BY id DESC LIMIT ?",
                (rs, i) -> new AuditEntry(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getTimestamp(6).toInstant()),
                limit);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    public record AuditEntry(long id, String actor, String action, String target, String details, Instant at) {
    }
}
