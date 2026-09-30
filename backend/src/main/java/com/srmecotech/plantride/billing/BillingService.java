package com.srmecotech.plantride.billing;

import com.srmecotech.plantride.billing.dto.BillingDtos.BillingSummaryDto;
import com.srmecotech.plantride.billing.dto.BillingDtos.ComplianceWatch;
import com.srmecotech.plantride.billing.dto.BillingDtos.CostCentreCharge;
import com.srmecotech.plantride.billing.dto.BillingDtos.Totals;
import com.srmecotech.plantride.billing.dto.BillingDtos.VendorReconciliation;
import com.srmecotech.plantride.booking.Booking;
import com.srmecotech.plantride.common.error.InvalidRequestException;
import com.srmecotech.plantride.common.settings.SettingsService;
import com.srmecotech.plantride.common.settings.SettingsService.CostSharingRule;
import com.srmecotech.plantride.common.util.Money;
import com.srmecotech.plantride.masterdata.ComplianceService;
import com.srmecotech.plantride.masterdata.DriverRepository;
import com.srmecotech.plantride.masterdata.RoutePlan;
import com.srmecotech.plantride.masterdata.Vehicle;
import com.srmecotech.plantride.masterdata.VehicleRepository;
import com.srmecotech.plantride.masterdata.dto.MasterDataDtos.DocState;
import com.srmecotech.plantride.masterdata.dto.MasterDataDtos.DocumentStatus;
import com.srmecotech.plantride.notification.NotificationService;
import com.srmecotech.plantride.trip.Trip;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Trip completed -> distance and riders -> sharing rule -> cost centre charged.
 * Every rupee of a trip's cost lands on exactly one ride; rounding residue
 * goes to the last share so the shares always add up to the trip cost.
 */
@Service
public class BillingService {

    private static final DateTimeFormatter CSV_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CostAllocationRepository allocationRepository;
    private final SettingsService settings;
    private final NotificationService notificationService;
    private final NamedParameterJdbcTemplate jdbc;
    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;
    private final ComplianceService complianceService;
    private final Clock clock;

    public BillingService(CostAllocationRepository allocationRepository, SettingsService settings,
                          NotificationService notificationService, NamedParameterJdbcTemplate jdbc,
                          VehicleRepository vehicleRepository, DriverRepository driverRepository,
                          ComplianceService complianceService, Clock clock) {
        this.allocationRepository = allocationRepository;
        this.settings = settings;
        this.notificationService = notificationService;
        this.jdbc = jdbc;
        this.vehicleRepository = vehicleRepository;
        this.driverRepository = driverRepository;
        this.complianceService = complianceService;
        this.clock = clock;
    }

    /**
     * Splits a completed trip's cost across the rides it carried and posts one
     * allocation per ride. Idempotent per booking (uk_alloc_booking backs this up).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void allocate(Trip trip, RoutePlan plan, List<Booking> completedRides) {
        if (completedRides.isEmpty() || trip.getCostAmount() == null) {
            return;
        }
        CostSharingRule rule = settings.costSharingRule();
        List<BigDecimal> weights = new ArrayList<>();
        for (Booking b : completedRides) {
            BigDecimal seats = BigDecimal.valueOf(b.getSeats());
            weights.add(rule == CostSharingRule.EQUAL ? seats : plan.kmBetween(b.getFromSeq(), b.getToSeq()).multiply(seats));
        }
        BigDecimal totalWeight = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalWeight.signum() == 0) {
            weights.replaceAll(w -> BigDecimal.ONE);
            totalWeight = BigDecimal.valueOf(weights.size());
        }

        BigDecimal tripCost = trip.getCostAmount();
        BigDecimal allocated = BigDecimal.ZERO;
        String period = YearMonth.from(trip.getEndedAt()).toString();
        LocalDateTime now = LocalDateTime.now(clock);
        for (int i = 0; i < completedRides.size(); i++) {
            Booking ride = completedRides.get(i);
            boolean last = i == completedRides.size() - 1;
            BigDecimal share = last
                    ? tripCost.subtract(allocated)
                    : tripCost.multiply(weights.get(i)).divide(totalWeight, 2, RoundingMode.HALF_UP);
            allocated = allocated.add(share);
            ride.setCostAmount(share);
            if (allocationRepository.existsByBookingId(ride.getId())) {
                continue;
            }
            CostAllocation allocation = new CostAllocation();
            allocation.setBooking(ride);
            allocation.setTrip(trip);
            allocation.setCostCentre(ride.getCostCentre());
            allocation.setPeriod(period);
            allocation.setDistanceKm(Money.km(ride.getDistanceKm() == null ? BigDecimal.ZERO : ride.getDistanceKm()));
            allocation.setAmount(share);
            allocation.setSharingRule(rule.name());
            allocation.setRidersOnTrip(completedRides.size());
            allocation.setCreatedAt(now);
            allocationRepository.save(allocation);

            notificationService.notifyBooker(ride, NotificationService.TRIP_COMPLETED,
                    "Charged ₹" + share.toPlainString() + " to " + ride.getCostCentre().getCode(),
                    ride.getFromStop().getName() + " to " + ride.getToStop().getName() + " · "
                            + ride.getDistanceKm() + " km · shared with " + (completedRides.size() - 1)
                            + " other" + (completedRides.size() == 2 ? "" : "s") + " · "
                            + ride.getCostCentre().getName() + " (" + rule.name().toLowerCase() + " split)");
        }
    }

    @Transactional(readOnly = true)
    public BillingSummaryDto summary(String periodParam) {
        YearMonth period = parsePeriod(periodParam);
        LocalDateTime from = period.atDay(1).atStartOfDay();
        LocalDateTime to = period.plusMonths(1).atDay(1).atStartOfDay();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("period", period.toString())
                .addValue("from", Timestamp.valueOf(from))
                .addValue("to", Timestamp.valueOf(to));

        List<CostCentreCharge> costCentres = jdbc.query("""
                SELECT cc.code, cc.name, cc.monthly_budget,
                       COUNT(DISTINCT ca.trip_id)       AS trips,
                       COUNT(ca.id)                     AS rides,
                       COALESCE(SUM(ca.distance_km), 0) AS km,
                       COALESCE(SUM(ca.amount), 0)      AS amount
                FROM cost_centre cc
                LEFT JOIN cost_allocation ca ON ca.cost_centre_id = cc.id AND ca.period = :period
                WHERE cc.active = TRUE
                GROUP BY cc.id, cc.code, cc.name, cc.monthly_budget
                ORDER BY amount DESC, cc.code
                """, params, (rs, i) -> {
            BigDecimal budget = rs.getBigDecimal("monthly_budget");
            BigDecimal amount = rs.getBigDecimal("amount");
            int pct = budget.signum() == 0 ? 0
                    : amount.multiply(BigDecimal.valueOf(100)).divide(budget, 0, RoundingMode.HALF_UP).intValue();
            String status = pct >= 100 ? "OVER" : pct >= 80 ? "WARN" : "OK";
            return new CostCentreCharge(rs.getString("code"), rs.getString("name"), rs.getLong("trips"),
                    rs.getLong("rides"), Money.km(rs.getBigDecimal("km")), Money.rupees(amount),
                    Money.rupees(budget), pct, status);
        });

        Totals totals = jdbc.queryForObject("""
                SELECT COUNT(*) AS trips,
                       COALESCE(SUM(t.distance_km), 0) AS gps_km,
                       COALESCE(SUM(t.cost_amount), 0) AS amount,
                       (SELECT COUNT(*) FROM cost_allocation ca WHERE ca.period = :period) AS rides
                FROM trip t
                WHERE t.status = 'COMPLETED' AND t.ended_at >= :from AND t.ended_at < :to
                """, params, (rs, i) -> {
            long trips = rs.getLong("trips");
            long rides = rs.getLong("rides");
            BigDecimal perTrip = trips == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(rides).divide(BigDecimal.valueOf(trips), 2, RoundingMode.HALF_UP);
            return new Totals(trips, rides, Money.km(rs.getBigDecimal("gps_km")), Money.rupees(rs.getBigDecimal("amount")), perTrip);
        });

        List<VendorReconciliation> vendors = jdbc.query("""
                SELECT v.code, v.name, vc.claimed_km, vc.claimed_amount,
                       COALESCE(g.gps_km, 0) AS gps_km, COALESCE(g.gps_amount, 0) AS gps_amount
                FROM vendor v
                LEFT JOIN vendor_claim vc ON vc.vendor_id = v.id AND vc.period = :period
                LEFT JOIN (SELECT vh.vendor_id, SUM(t.distance_km) AS gps_km, SUM(t.cost_amount) AS gps_amount
                           FROM trip t JOIN vehicle vh ON vh.id = t.vehicle_id
                           WHERE t.status = 'COMPLETED' AND t.ended_at >= :from AND t.ended_at < :to
                             AND vh.vendor_id IS NOT NULL
                           GROUP BY vh.vendor_id) g ON g.vendor_id = v.id
                WHERE v.active = TRUE
                ORDER BY v.code
                """, params, (rs, i) -> reconcile(rs.getString("code"), rs.getString("name"),
                rs.getBigDecimal("claimed_km"), rs.getBigDecimal("gps_km"),
                rs.getBigDecimal("claimed_amount"), rs.getBigDecimal("gps_amount")));

        return new BillingSummaryDto(period.toString(), !period.isBefore(YearMonth.now(clock)),
                settings.costSharingRule().name(), totals, costCentres, vendors, complianceWatch());
    }

    /** Ride-level lines for the month-end ERP import. */
    @Transactional(readOnly = true)
    public String exportCsv(String periodParam) {
        YearMonth period = parsePeriod(periodParam);
        StringBuilder csv = new StringBuilder();
        csv.append("period,cost_centre,cost_centre_name,booking_code,trip_code,completed_at,route,from_stop,to_stop,")
                .append("personnel_no,rider_name,km,amount_inr,sharing_rule\n");
        jdbc.query("""
                SELECT ca.period, cc.code AS cc_code, cc.name AS cc_name, b.booking_code, t.trip_code, t.ended_at,
                       r.code AS route_code, fs.name AS from_name, ts.name AS to_name, e.p_no, b.rider_name,
                       ca.distance_km, ca.amount, ca.sharing_rule
                FROM cost_allocation ca
                JOIN cost_centre cc ON cc.id = ca.cost_centre_id
                JOIN booking b      ON b.id = ca.booking_id
                JOIN trip t         ON t.id = ca.trip_id
                JOIN route r        ON r.id = b.route_id
                JOIN stop fs        ON fs.id = b.from_stop_id
                JOIN stop ts        ON ts.id = b.to_stop_id
                JOIN employee e     ON e.id = b.employee_id
                WHERE ca.period = :period
                ORDER BY cc.code, t.ended_at, b.booking_code
                """, new MapSqlParameterSource("period", period.toString()), rs -> {
            csv.append(String.join(",",
                    cell(rs.getString("period")),
                    cell(rs.getString("cc_code")),
                    cell(rs.getString("cc_name")),
                    cell(rs.getString("booking_code")),
                    cell(rs.getString("trip_code")),
                    cell(rs.getTimestamp("ended_at").toLocalDateTime().format(CSV_TIME)),
                    cell(rs.getString("route_code")),
                    cell(rs.getString("from_name")),
                    cell(rs.getString("to_name")),
                    cell(rs.getString("p_no")),
                    cell(rs.getString("rider_name")),
                    rs.getBigDecimal("distance_km").toPlainString(),
                    rs.getBigDecimal("amount").toPlainString(),
                    cell(rs.getString("sharing_rule"))));
            csv.append('\n');
        });
        return csv.toString();
    }

    public YearMonth parsePeriod(String periodParam) {
        if (periodParam == null || periodParam.isBlank()) {
            return YearMonth.now(clock);
        }
        try {
            YearMonth period = YearMonth.parse(periodParam.trim());
            if (period.isAfter(YearMonth.now(clock))) {
                throw new InvalidRequestException("BAD_PERIOD", "Billing periods in the future have no charges yet.");
            }
            return period;
        } catch (DateTimeParseException ex) {
            throw new InvalidRequestException("BAD_PERIOD", "Use a billing period like 2026-09.");
        }
    }

    private ComplianceWatch complianceWatch() {
        int expiring = 0;
        int expired = 0;
        int blockedVehicles = 0;
        int compliantVehicles = 0;
        List<Vehicle> vehicles = vehicleRepository.findAll();
        for (Vehicle v : vehicles) {
            List<DocumentStatus> docs = complianceService.vehicleDocuments(v);
            long exp = docs.stream().filter(d -> d.state() == DocState.EXPIRED).count();
            long soon = docs.stream().filter(d -> d.state() == DocState.EXPIRING).count();
            expired += (int) exp;
            expiring += (int) soon;
            if (exp > 0) {
                blockedVehicles++;
            } else if (soon == 0) {
                compliantVehicles++;
            }
        }
        int blockedDrivers = 0;
        for (var d : driverRepository.findAllWithUser()) {
            List<DocumentStatus> docs = complianceService.driverDocuments(d);
            long exp = docs.stream().filter(doc -> doc.state() == DocState.EXPIRED).count();
            expired += (int) exp;
            expiring += (int) docs.stream().filter(doc -> doc.state() == DocState.EXPIRING).count();
            if (exp > 0) {
                blockedDrivers++;
            }
        }
        return new ComplianceWatch(expiring, expired, blockedVehicles, blockedDrivers, compliantVehicles, vehicles.size());
    }

    private static VendorReconciliation reconcile(String code, String name, BigDecimal claimedKm, BigDecimal gpsKm,
                                                  BigDecimal claimedAmount, BigDecimal gpsAmount) {
        if (claimedKm == null) {
            return new VendorReconciliation(code, name, null, Money.km(gpsKm), null, null, null,
                    Money.rupees(gpsAmount), "NO_CLAIM");
        }
        BigDecimal gap = claimedKm.subtract(gpsKm);
        BigDecimal gapPct = gpsKm.signum() == 0 ? BigDecimal.ZERO
                : gap.multiply(BigDecimal.valueOf(100)).divide(gpsKm, 1, RoundingMode.HALF_UP);
        // GPS kilometres are the contractual source. Within 1% or 5 km the claim is accepted.
        boolean matched = gap.abs().compareTo(BigDecimal.valueOf(5)) <= 0 || gapPct.abs().compareTo(BigDecimal.ONE) <= 0;
        return new VendorReconciliation(code, name, Money.km(claimedKm), Money.km(gpsKm), Money.km(gap), gapPct,
                Money.rupees(claimedAmount), Money.rupees(gpsAmount), matched ? "MATCHED" : "DISPUTE");
    }

    /** RFC 4180 quoting, plus a guard against spreadsheet formula injection. */
    private static String cell(String value) {
        if (value == null) {
            return "";
        }
        String v = value;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    public String csvFileName(String periodParam) {
        return "plant-ride-costs-" + parsePeriod(periodParam) + ".csv";
    }
}
