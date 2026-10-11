package com.srmecotech.ecoride.rider;

import com.srmecotech.ecoride.common.error.InvalidRequestException;
import com.srmecotech.ecoride.common.util.Money;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** The rider's EcoRide wallet: every balance change has a matching statement line. */
@Service
public class WalletService {

    private final RiderRepository riders;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public WalletService(RiderRepository riders, JdbcTemplate jdbc, Clock clock) {
        this.riders = riders;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** @return true if the wallet covered the amount (and was debited); false leaves it untouched. */
    @Transactional
    public boolean tryDebit(long riderId, Long rideId, String type, BigDecimal amount, String note) {
        BigDecimal value = Money.of(amount);
        if (value.signum() <= 0) {
            return true;
        }
        if (riders.debitWallet(riderId, value) == 0) {
            return false;
        }
        statement(riderId, rideId, type, value.negate(), note);
        return true;
    }

    @Transactional
    public void credit(long riderId, Long rideId, String type, BigDecimal amount, String note) {
        BigDecimal value = Money.of(amount);
        if (value.signum() <= 0) {
            throw new InvalidRequestException("AMOUNT_INVALID", "The amount must be more than zero.");
        }
        riders.creditWallet(riderId, value);
        statement(riderId, rideId, type, value, note);
    }

    public BigDecimal balance(long riderId) {
        BigDecimal balance = riders.walletBalance(riderId);
        return balance == null ? Money.ZERO : balance;
    }

    public List<WalletLine> statement(long riderId, int limit) {
        return jdbc.query("""
                        SELECT id, ride_id, type, amount, balance_after, note, created_at
                        FROM wallet_txn WHERE rider_id = ? ORDER BY id DESC LIMIT ?
                        """,
                (rs, i) -> new WalletLine(rs.getLong(1), (Long) rs.getObject(2), rs.getString(3), rs.getBigDecimal(4),
                        rs.getBigDecimal(5), rs.getString(6), rs.getTimestamp(7).toInstant()),
                riderId, limit);
    }

    private void statement(long riderId, Long rideId, String type, BigDecimal signedAmount, String note) {
        jdbc.update("""
                        INSERT INTO wallet_txn (rider_id, ride_id, type, amount, balance_after, note, created_at)
                        SELECT ?, ?, ?, ?, wallet_balance, ?, ? FROM rider WHERE id = ?
                        """,
                riderId, rideId, type, signedAmount, note, Timestamp.from(clock.instant()), riderId);
    }

    public record WalletLine(long id, Long rideId, String type, BigDecimal amount, BigDecimal balanceAfter, String note,
                             Instant at) {
    }
}
