package com.srmecotech.ecoride.driver;

import com.srmecotech.ecoride.common.util.Money;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * What the platform owes each driver. Positive lines are owed to the driver (fare share,
 * tips, cancellation fees); negative lines reduce it (penalties, cash the driver kept on
 * a cash ride, payouts already made). The balance is the next payout.
 */
@Service
public class DriverLedgerService {

    public static final String EARNING = "EARNING";
    public static final String TIP = "TIP";
    public static final String CANCEL_FEE = "CANCEL_FEE";
    public static final String PENALTY = "PENALTY";
    public static final String CASH_COLLECTED = "CASH_COLLECTED";
    public static final String PAYOUT = "PAYOUT";
    public static final String ADJUSTMENT = "ADJUSTMENT";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public DriverLedgerService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public void add(long driverId, Long rideId, String type, BigDecimal amount, String note) {
        jdbc.update("INSERT INTO driver_ledger (driver_id, ride_id, type, amount, note, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                driverId, rideId, type, Money.of(amount), note, Timestamp.from(clock.instant()));
    }

    public BigDecimal balance(long driverId) {
        BigDecimal balance = jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM driver_ledger WHERE driver_id = ?",
                BigDecimal.class, driverId);
        return Money.of(balance);
    }

    /** Sum of the given line types in [from, to). */
    public BigDecimal sum(long driverId, Instant from, Instant to, String... types) {
        String in = String.join(",", java.util.Collections.nCopies(types.length, "?"));
        Object[] args = new Object[3 + types.length];
        args[0] = driverId;
        args[1] = Timestamp.from(from);
        args[2] = Timestamp.from(to);
        System.arraycopy(types, 0, args, 3, types.length);
        BigDecimal sum = jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM driver_ledger WHERE driver_id = ? "
                + "AND created_at >= ? AND created_at < ? AND type IN (" + in + ")", BigDecimal.class, args);
        return Money.of(sum);
    }

    public List<LedgerLine> lines(long driverId, int limit) {
        return jdbc.query("""
                        SELECT l.id, l.ride_id, r.code, l.type, l.amount, l.note, l.created_at
                        FROM driver_ledger l LEFT JOIN ride r ON r.id = l.ride_id
                        WHERE l.driver_id = ? ORDER BY l.id DESC LIMIT ?
                        """,
                (rs, i) -> new LedgerLine(rs.getLong(1), (Long) rs.getObject(2), rs.getString(3), rs.getString(4),
                        rs.getBigDecimal(5), rs.getString(6), rs.getTimestamp(7).toInstant()),
                driverId, limit);
    }

    public record LedgerLine(long id, Long rideId, String rideCode, String type, BigDecimal amount, String note,
                             Instant at) {
    }
}
