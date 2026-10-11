package com.srmecotech.ecoride.admin;

import com.srmecotech.ecoride.common.config.ClockConfig;
import com.srmecotech.ecoride.common.error.NotFoundException;
import com.srmecotech.ecoride.common.util.Money;
import com.srmecotech.ecoride.driver.Driver;
import com.srmecotech.ecoride.driver.DriverLedgerService;
import com.srmecotech.ecoride.driver.DriverRepository;
import com.srmecotech.ecoride.driver.DriverStatus;
import com.srmecotech.ecoride.driver.Availability;
import com.srmecotech.ecoride.geo.GeoPoint;
import com.srmecotech.ecoride.pricing.SurgeService;
import com.srmecotech.ecoride.pricing.SurgeZoneRepository;
import com.srmecotech.ecoride.pricing.VehicleType;
import com.srmecotech.ecoride.pricing.VehicleTypeRepository;
import com.srmecotech.ecoride.ride.Ride;
import com.srmecotech.ecoride.ride.RideLog;
import com.srmecotech.ecoride.ride.RideRepository;
import com.srmecotech.ecoride.ride.RideStatus;
import com.srmecotech.ecoride.ride.RideViewService;
import com.srmecotech.ecoride.ride.RideViews;
import com.srmecotech.ecoride.rider.Rider;
import com.srmecotech.ecoride.rider.RiderAccountService;
import com.srmecotech.ecoride.rider.RiderRepository;
import com.srmecotech.ecoride.rider.RiderStatus;
import com.srmecotech.ecoride.rider.WalletService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Read side of the admin console: dashboard, live map, searches and detail pages. Days are city days (IST). */
@Service
@Transactional(readOnly = true)
public class AdminQueryService {

    /** MySQL converts UTC timestamps to city time with a fixed offset (no time zone tables needed). */
    private static final String CITY = "'+00:00', '+05:30'";

    private static final String RIDE_ROW_SELECT = """
            SELECT r.id, r.code, r.status, r.vehicle_type, ri.name AS rider_name, d.name AS driver_name,
                   r.pickup_address, r.drop_address, r.requested_at, r.completed_at,
                   CASE WHEN r.status = 'COMPLETED' THEN r.fare_total
                        WHEN r.status = 'CANCELLED' THEN r.cancellation_fee ELSE r.est_fare END AS amount,
                   r.payment_method, r.payment_status,
                   CASE WHEN r.status = 'COMPLETED' AND r.actual_distance_m > 0 THEN r.actual_distance_m
                        ELSE r.est_distance_m END AS metres,
                   r.simulated
            FROM ride r JOIN rider ri ON ri.id = r.rider_id LEFT JOIN driver d ON d.id = r.driver_id
            """;

    private static final RowMapper<AdminDtos.RideRow> RIDE_ROW = (rs, i) -> new AdminDtos.RideRow(
            rs.getLong("id"), rs.getString("code"), rs.getString("status"), rs.getString("vehicle_type"),
            rs.getString("rider_name"), rs.getString("driver_name"), rs.getString("pickup_address"),
            rs.getString("drop_address"), instant(rs.getTimestamp("requested_at")), instant(rs.getTimestamp("completed_at")),
            rs.getBigDecimal("amount"), rs.getString("payment_method"), rs.getString("payment_status"),
            Math.round(rs.getInt("metres") / 100d) / 10d, rs.getBoolean("simulated"));

    private final JdbcTemplate jdbc;
    private final RideRepository rides;
    private final DriverRepository drivers;
    private final RiderRepository riders;
    private final VehicleTypeRepository vehicleTypes;
    private final SurgeZoneRepository zones;
    private final SurgeService surge;
    private final DriverLedgerService ledger;
    private final WalletService wallet;
    private final RiderAccountService riderAccounts;
    private final RideViewService views;
    private final RideLog rideLog;
    private final Clock clock;

    public AdminQueryService(JdbcTemplate jdbc, RideRepository rides, DriverRepository drivers, RiderRepository riders,
                             VehicleTypeRepository vehicleTypes, SurgeZoneRepository zones, SurgeService surge,
                             DriverLedgerService ledger, WalletService wallet, RiderAccountService riderAccounts,
                             RideViewService views, RideLog rideLog, Clock clock) {
        this.jdbc = jdbc;
        this.rides = rides;
        this.drivers = drivers;
        this.riders = riders;
        this.vehicleTypes = vehicleTypes;
        this.zones = zones;
        this.surge = surge;
        this.ledger = ledger;
        this.wallet = wallet;
        this.riderAccounts = riderAccounts;
        this.views = views;
        this.rideLog = rideLog;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ dashboard

    public AdminDtos.Dashboard dashboard() {
        LocalDate today = LocalDate.now(clock);
        Timestamp from = ts(startOf(today));
        Timestamp to = ts(startOf(today.plusDays(1)));

        Map<String, Object> rideStats = jdbc.queryForMap("""
                SELECT COUNT(*) AS requested,
                       COALESCE(SUM(status IN ('CANCELLED', 'NO_DRIVERS')), 0) AS cancelled
                FROM ride WHERE requested_at >= ? AND requested_at < ?
                """, from, to);
        Map<String, Object> done = jdbc.queryForMap("""
                SELECT COUNT(*) AS completed, COALESCE(SUM(fare_total), 0) AS gmv, COALESCE(SUM(commission), 0) AS commission,
                       AVG(fare_total) AS avg_fare, AVG(driver_rating) AS avg_rating
                FROM ride WHERE status = 'COMPLETED' AND completed_at >= ? AND completed_at < ?
                """, from, to);
        Double avgPickupS = jdbc.queryForObject("""
                SELECT AVG(TIMESTAMPDIFF(SECOND, accepted_at, arrived_at)) FROM ride
                WHERE arrived_at >= ? AND arrived_at < ? AND accepted_at IS NOT NULL
                """, Double.class, from, to);
        Map<String, Object> now = jdbc.queryForMap("""
                SELECT COALESCE(SUM(status IN ('ACCEPTED', 'ARRIVED', 'IN_PROGRESS')), 0) AS active,
                       COALESCE(SUM(status = 'SEARCHING'), 0) AS searching
                FROM ride WHERE status IN ('SEARCHING', 'ACCEPTED', 'ARRIVED', 'IN_PROGRESS')
                """);
        Map<String, Object> fleet = jdbc.queryForMap("""
                SELECT COALESCE(SUM(availability = 'ONLINE'), 0) AS online, COALESCE(SUM(availability = 'ON_TRIP'), 0) AS on_trip,
                       COALESCE(SUM(status = 'APPROVED'), 0) AS approved, COALESCE(SUM(status = 'PENDING_APPROVAL'), 0) AS pending
                FROM driver
                """);
        Map<String, Object> people = jdbc.queryForMap("""
                SELECT COUNT(*) AS riders, COALESCE(SUM(created_at >= ? AND created_at < ?), 0) AS new_today FROM rider
                """, from, to);
        Integer openSos = jdbc.queryForObject("SELECT COUNT(*) FROM sos_alert WHERE status = 'OPEN'", Integer.class);

        int requested = num(rideStats.get("requested"));
        int cancelled = num(rideStats.get("cancelled"));
        AdminDtos.Kpis kpis = new AdminDtos.Kpis(
                requested,
                num(done.get("completed")),
                cancelled,
                num(now.get("active")),
                num(now.get("searching")),
                num(fleet.get("online")),
                num(fleet.get("on_trip")),
                num(fleet.get("approved")),
                num(fleet.get("pending")),
                num(people.get("riders")),
                num(people.get("new_today")),
                money(done.get("gmv")),
                money(done.get("commission")),
                money(done.get("avg_fare")),
                avgPickupS == null ? null : round1(avgPickupS / 60d),
                requested == 0 ? null : round1(cancelled * 100d / requested),
                done.get("avg_rating") == null ? null : round1(((Number) done.get("avg_rating")).doubleValue()),
                openSos == null ? 0 : openSos);

        List<AdminDtos.HourPoint> hours = new ArrayList<>();
        Map<Integer, Integer> byHour = new HashMap<>();
        jdbc.query("""
                SELECT HOUR(CONVERT_TZ(requested_at, %s)) AS h, COUNT(*) AS n FROM ride
                WHERE requested_at >= ? AND requested_at < ? GROUP BY h
                """.formatted(CITY), rs -> {
            byHour.put(rs.getInt("h"), rs.getInt("n"));
        }, from, to);
        for (int h = 0; h < 24; h++) {
            hours.add(new AdminDtos.HourPoint(h, byHour.getOrDefault(h, 0)));
        }

        return new AdminDtos.Dashboard(kpis, daily(14), hours, byType(30), recentRides(8), sosRows("OPEN", 10),
                pendingDrivers());
    }

    public List<AdminDtos.DayPoint> daily(int days) {
        LocalDate today = LocalDate.now(clock);
        LocalDate first = today.minusDays(days - 1L);
        Map<LocalDate, int[]> counts = new HashMap<>();
        jdbc.query("""
                SELECT DATE(CONVERT_TZ(requested_at, %s)) AS d, COUNT(*) AS n,
                       COALESCE(SUM(status IN ('CANCELLED', 'NO_DRIVERS')), 0) AS c
                FROM ride WHERE requested_at >= ? GROUP BY d
                """.formatted(CITY), rs -> {
            counts.put(rs.getDate("d").toLocalDate(), new int[]{rs.getInt("n"), rs.getInt("c")});
        }, ts(startOf(first)));
        Map<LocalDate, Object[]> money = new HashMap<>();
        jdbc.query("""
                SELECT DATE(CONVERT_TZ(completed_at, %s)) AS d, COUNT(*) AS n, COALESCE(SUM(fare_total), 0) AS gmv,
                       COALESCE(SUM(commission), 0) AS commission
                FROM ride WHERE status = 'COMPLETED' AND completed_at >= ? GROUP BY d
                """.formatted(CITY), rs -> {
            money.put(rs.getDate("d").toLocalDate(), new Object[]{rs.getInt("n"), rs.getBigDecimal("gmv"), rs.getBigDecimal("commission")});
        }, ts(startOf(first)));
        List<AdminDtos.DayPoint> points = new ArrayList<>();
        for (LocalDate d = first; !d.isAfter(today); d = d.plusDays(1)) {
            int[] c = counts.getOrDefault(d, new int[]{0, 0});
            Object[] m = money.getOrDefault(d, new Object[]{0, BigDecimal.ZERO, BigDecimal.ZERO});
            points.add(new AdminDtos.DayPoint(d, c[0], (Integer) m[0], c[1], Money.of((BigDecimal) m[1]), Money.of((BigDecimal) m[2])));
        }
        return points;
    }

    public List<AdminDtos.TypeSplit> byType(int days) {
        Map<String, String> names = new HashMap<>();
        vehicleTypes.findAll().forEach(vt -> names.put(vt.getCode(), vt.getName()));
        return jdbc.query("""
                        SELECT vehicle_type, COUNT(*) AS n, COALESCE(SUM(fare_total), 0) AS gmv FROM ride
                        WHERE status = 'COMPLETED' AND completed_at >= ? GROUP BY vehicle_type ORDER BY gmv DESC
                        """,
                (rs, i) -> new AdminDtos.TypeSplit(rs.getString(1), names.getOrDefault(rs.getString(1), rs.getString(1)),
                        rs.getInt(2), Money.of(rs.getBigDecimal(3))),
                ts(startOf(LocalDate.now(clock).minusDays(days - 1L))));
    }

    // ------------------------------------------------------------------ live map

    public AdminDtos.Live live() {
        Instant now = clock.instant();
        List<Ride> active = rides.findByStatusIn(RideStatus.ACTIVE);
        Map<Long, Ride> rideByDriver = new HashMap<>();
        Set<Long> riderIds = new HashSet<>();
        for (Ride r : active) {
            riderIds.add(r.getRiderId());
            if (r.getDriverId() != null && r.getStatus().isAssigned()) {
                rideByDriver.put(r.getDriverId(), r);
            }
        }
        Set<Long> withOffer = new HashSet<>(jdbc.queryForList(
                "SELECT driver_id FROM ride_offer WHERE status = 'PENDING'", Long.class));
        Map<Long, Driver> driverById = new HashMap<>();
        List<AdminDtos.LiveDriver> driverDots = new ArrayList<>();
        int online = 0;
        int onTrip = 0;
        for (Driver d : drivers.findAllOnMap()) {
            driverById.put(d.getId(), d);
            Ride r = rideByDriver.get(d.getId());
            if (d.getAvailability() == Availability.ONLINE) {
                online++;
            } else if (d.getAvailability() == Availability.ON_TRIP) {
                onTrip++;
            }
            driverDots.add(new AdminDtos.LiveDriver(d.getId(), d.getName(), d.getVehicleType(), d.getVehiclePlate(),
                    d.getAvailability().name(), d.getLat(), d.getLng(), d.getHeading(),
                    d.getLocatedAt() == null ? null : (int) Duration.between(d.getLocatedAt(), now).toSeconds(),
                    d.isSimulated(), r == null ? null : r.getId(), r == null ? null : r.getCode(),
                    r == null ? null : r.getStatus().name(), withOffer.contains(d.getId())));
        }
        Map<Long, String> riderNames = new HashMap<>();
        riders.findAllById(riderIds).forEach(r -> riderNames.put(r.getId(), r.getName()));
        List<AdminDtos.LiveRide> rideDots = new ArrayList<>();
        int searching = 0;
        int inProgress = 0;
        for (Ride r : active) {
            if (r.getStatus() == RideStatus.SEARCHING) {
                searching++;
            } else if (r.getStatus() == RideStatus.IN_PROGRESS) {
                inProgress++;
            }
            Driver d = r.getDriverId() == null ? null : driverById.get(r.getDriverId());
            rideDots.add(new AdminDtos.LiveRide(r.getId(), r.getCode(), r.getStatus().name(), r.getVehicleType(),
                    riderNames.get(r.getRiderId()), d == null ? null : d.getName(),
                    new RideViews.Place(r.getPickupLat(), r.getPickupLng(), r.getPickupAddress()),
                    new RideViews.Place(r.getDropLat(), r.getDropLng(), r.getDropAddress()),
                    d == null ? null : d.getLat(), d == null ? null : d.getLng(),
                    r.getStatus() == RideStatus.IN_PROGRESS || r.getStatus() == RideStatus.SEARCHING ? r.getRoutePolyline() : null,
                    r.getStatus() == RideStatus.ACCEPTED ? r.getPickupPolyline() : null,
                    r.isSimulated(), r.getRequestedAt()));
        }
        List<AdminDtos.LiveZone> zoneDots = zones.findAllByOrderByName().stream().map(z -> {
            SurgeService.SurgeInfo info = z.isActive() ? surge.forZone(z, new GeoPoint(z.getLat(), z.getLng())) : null;
            return new AdminDtos.LiveZone(z.getId(), z.getName(), z.getLat(), z.getLng(), z.getRadiusM(), z.getMode(),
                    info == null ? BigDecimal.ONE : info.multiplier(), z.isActive(),
                    info == null ? null : info.demand(), info == null ? null : info.supply());
        }).toList();
        return new AdminDtos.Live(driverDots, rideDots, zoneDots, online, onTrip, searching, inProgress);
    }

    // ------------------------------------------------------------------ rides

    public AdminDtos.PageDto<AdminDtos.RideRow> rides(String q, String status, String vehicleType, Long riderId,
                                                      Long driverId, boolean includeSimulated, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            where.append(" AND r.status = ?");
            args.add(status);
        }
        if (vehicleType != null && !vehicleType.isBlank()) {
            where.append(" AND r.vehicle_type = ?");
            args.add(vehicleType);
        }
        if (riderId != null) {
            where.append(" AND r.rider_id = ?");
            args.add(riderId);
        }
        if (driverId != null) {
            where.append(" AND r.driver_id = ?");
            args.add(driverId);
        }
        if (!includeSimulated) {
            where.append(" AND r.simulated = FALSE");
        }
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase() + "%";
            where.append(" AND (LOWER(r.code) LIKE ? OR LOWER(ri.name) LIKE ? OR LOWER(d.name) LIKE ?"
                    + " OR LOWER(r.pickup_address) LIKE ? OR LOWER(r.drop_address) LIKE ?)");
            for (int i = 0; i < 5; i++) {
                args.add(like);
            }
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM ride r JOIN rider ri ON ri.id = r.rider_id "
                + "LEFT JOIN driver d ON d.id = r.driver_id" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add(page * size);
        List<AdminDtos.RideRow> items = jdbc.query(RIDE_ROW_SELECT + where + " ORDER BY r.id DESC LIMIT ? OFFSET ?",
                RIDE_ROW, pageArgs.toArray());
        return new AdminDtos.PageDto<>(items, page, size, total == null ? 0 : total);
    }

    public List<AdminDtos.RideRow> recentRides(int limit) {
        return jdbc.query(RIDE_ROW_SELECT + " ORDER BY r.id DESC LIMIT ?", RIDE_ROW, limit);
    }

    public AdminDtos.RideDetail ride(long id) {
        Ride ride = rides.findById(id).orElseThrow(() -> new NotFoundException("Ride", id));
        AdminDtos.RideRow row = jdbc.query(RIDE_ROW_SELECT + " WHERE r.id = ?", RIDE_ROW, id).get(0);
        Rider rider = riders.findById(ride.getRiderId()).orElseThrow();
        Driver driver = ride.getDriverId() == null ? null : drivers.findById(ride.getDriverId()).orElse(null);
        List<AdminDtos.OfferRow> offerRows = jdbc.query("""
                        SELECT o.driver_id, d.name, o.status, o.distance_m, o.eta_s, o.offered_at, o.responded_at, d.simulated
                        FROM ride_offer o JOIN driver d ON d.id = o.driver_id WHERE o.ride_id = ? ORDER BY o.id
                        """,
                (rs, i) -> new AdminDtos.OfferRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4),
                        rs.getInt(5), instant(rs.getTimestamp(6)), instant(rs.getTimestamp(7)), rs.getBoolean(8)),
                id);
        List<AdminDtos.PaymentRow> paymentRows = payments(" WHERE p.ride_id = ?", List.of(id), 50);
        List<AdminDtos.SosRow> sos = sosRows(" WHERE s.ride_id = ?", List.of(id), 20);
        return new AdminDtos.RideDetail(
                row,
                new RideViews.Place(ride.getPickupLat(), ride.getPickupLng(), ride.getPickupAddress()),
                new RideViews.Place(ride.getDropLat(), ride.getDropLng(), ride.getDropAddress()),
                ride.getOtp(),
                ride.getEstDistanceM(),
                ride.getEstDurationS(),
                ride.getActualDistanceM(),
                ride.getPickupTravelM(),
                ride.getRoutePolyline(),
                ride.getPickupPolyline(),
                ride.getRouteProvider(),
                ride.getSurgeMultiplier(),
                ride.getPromoCode(),
                ride.getQuote(),
                ride.getFare(),
                ride.getFarePolicy(),
                ride.getCommission(),
                ride.getDriverEarning(),
                ride.getTip(),
                ride.getCancellationFee(),
                ride.getCancelledBy(),
                ride.getCancelReason(),
                ride.getDriverRating(),
                ride.getDriverFeedback(),
                ride.getRiderRating(),
                ride.getAcceptedAt(),
                ride.getArrivedAt(),
                ride.getStartedAt(),
                ride.getCancelledAt(),
                new AdminDtos.PersonBrief(rider.getId(), rider.getName(), rider.getPhone(), rider.getRatingAvg(),
                        rider.isSimulated() ? "Simulated rider" : null),
                driver == null ? null : new AdminDtos.PersonBrief(driver.getId(), driver.getName(), driver.getPhone(),
                        driver.getRatingAvg(), driver.vehicleLabel() + " · " + driver.getVehiclePlate()),
                views.timeline(id),
                offerRows,
                rideLog.trace(id, 400),
                paymentRows,
                sos);
    }

    // ------------------------------------------------------------------ drivers

    public AdminDtos.PageDto<AdminDtos.DriverRow> drivers(String q, DriverStatus status, Availability availability,
                                                          boolean includeSimulated, int page, int size) {
        Page<Driver> found = drivers.search(blank(q), status, availability, includeSimulated,
                PageRequest.of(page, size, Sort.by(Sort.Order.asc("simulated"), Sort.Order.desc("id"))));
        return new AdminDtos.PageDto<>(found.getContent().stream().map(this::driverRow).toList(), page, size,
                found.getTotalElements());
    }

    public List<AdminDtos.DriverRow> pendingDrivers() {
        return drivers.search(null, DriverStatus.PENDING_APPROVAL, null, true, PageRequest.of(0, 20, Sort.by("id")))
                .getContent().stream().map(this::driverRow).toList();
    }

    public AdminDtos.DriverDetail driver(long id) {
        Driver d = drivers.findById(id).orElseThrow(() -> new NotFoundException("Driver", id));
        LocalDate today = LocalDate.now(clock);
        Instant weekStart = startOf(today.minusDays(6));
        Instant end = startOf(today.plusDays(1));
        BigDecimal earned = ledger.sum(id, weekStart, end, DriverLedgerService.EARNING, DriverLedgerService.TIP,
                DriverLedgerService.CANCEL_FEE, DriverLedgerService.PENALTY, DriverLedgerService.ADJUSTMENT);
        int trips = (int) rides.countCompletedByDriverBetween(id, weekStart, end);
        return new AdminDtos.DriverDetail(driverRow(d), d.getEmail(), d.getLicenseNo(), d.getVehicleYear(),
                d.getStatusReason(), d.getApprovedAt(), d.getLastLoginAt(), d.getLat(), d.getLng(), earned, trips,
                jdbc.query(RIDE_ROW_SELECT + " WHERE r.driver_id = ? ORDER BY r.id DESC LIMIT 15", RIDE_ROW, id),
                ledger.lines(id, 30));
    }

    private AdminDtos.DriverRow driverRow(Driver d) {
        VehicleType vt = vehicleTypes.findById(d.getVehicleType()).orElse(null);
        Integer acceptance = d.getOffersReceived() == 0 ? null
                : (int) Math.round(d.getOffersAccepted() * 100.0 / d.getOffersReceived());
        return new AdminDtos.DriverRow(d.getId(), d.getName(), d.getPhone(), vt == null ? d.getVehicleType() : vt.getName(),
                d.vehicleLabel(), d.getVehiclePlate(), d.getStatus().name(), d.getAvailability().name(), d.getRatingAvg(),
                d.getRatingCount(), acceptance, rides.countCompletedByDriver(d.getId()), ledger.balance(d.getId()),
                d.isSimulated(), d.getCreatedAt(), d.getLocatedAt());
    }

    // ------------------------------------------------------------------ riders

    public AdminDtos.PageDto<AdminDtos.RiderRow> riders(String q, boolean includeSimulated, int page, int size) {
        Page<Rider> found = riders.search(blank(q), includeSimulated,
                PageRequest.of(page, size, Sort.by(Sort.Order.asc("simulated"), Sort.Order.desc("id"))));
        return new AdminDtos.PageDto<>(found.getContent().stream().map(this::riderRow).toList(), page, size,
                found.getTotalElements());
    }

    public AdminDtos.RiderDetail rider(long id) {
        Rider r = riders.findById(id).orElseThrow(() -> new NotFoundException("Rider", id));
        return new AdminDtos.RiderDetail(riderRow(r), r.getStatusReason(),
                jdbc.query(RIDE_ROW_SELECT + " WHERE r.rider_id = ? ORDER BY r.id DESC LIMIT 15", RIDE_ROW, id),
                wallet.statement(id, 20), riderAccounts.places(id));
    }

    private AdminDtos.RiderRow riderRow(Rider r) {
        return new AdminDtos.RiderRow(r.getId(), r.getName(), r.getPhone(), r.getEmail(), r.getStatus().name(),
                r.getRatingAvg(), r.getRatingCount(), wallet.balance(r.getId()), rides.countCompletedByRider(r.getId()),
                r.isSimulated(), r.getCreatedAt(), r.getLastLoginAt());
    }

    // ------------------------------------------------------------------ money

    public AdminDtos.Finance finance(int days) {
        LocalDate today = LocalDate.now(clock);
        Timestamp from = ts(startOf(today.minusDays(days - 1L)));
        Map<String, Object> t = jdbc.queryForMap("""
                SELECT COUNT(*) AS trips, COALESCE(SUM(fare_total), 0) AS gmv, COALESCE(SUM(tax), 0) AS tax,
                       COALESCE(SUM(discount), 0) AS discounts, COALESCE(SUM(commission), 0) AS commission,
                       COALESCE(SUM(driver_earning), 0) AS earnings, COALESCE(SUM(tip), 0) AS tips,
                       COALESCE(SUM(CASE WHEN payment_method = 'CASH' THEN fare_total ELSE 0 END), 0) AS cash
                FROM ride WHERE status = 'COMPLETED' AND completed_at >= ?
                """, from);
        BigDecimal cancelFees = jdbc.queryForObject("""
                SELECT COALESCE(SUM(cancellation_fee), 0) FROM ride WHERE status = 'CANCELLED' AND cancelled_at >= ?
                """, BigDecimal.class, from);
        BigDecimal gmv = money(t.get("gmv"));
        BigDecimal cash = money(t.get("cash"));
        BigDecimal commission = money(t.get("commission"));
        BigDecimal discounts = money(t.get("discounts"));
        AdminDtos.FinanceTotals totals = new AdminDtos.FinanceTotals(num(t.get("trips")), gmv, money(t.get("tax")),
                discounts, commission, money(t.get("earnings")), money(t.get("tips")), Money.of(cancelFees),
                Money.of(commission.subtract(discounts)), cash, Money.of(gmv.subtract(cash)));
        List<AdminDtos.MethodSplit> byMethod = jdbc.query("""
                        SELECT payment_method, COUNT(*), COALESCE(SUM(fare_total), 0) FROM ride
                        WHERE status = 'COMPLETED' AND completed_at >= ? GROUP BY payment_method ORDER BY 3 DESC
                        """,
                (rs, i) -> new AdminDtos.MethodSplit(rs.getString(1), rs.getInt(2), Money.of(rs.getBigDecimal(3))), from);
        Instant weekStart = startOf(today.minusDays(6));
        Timestamp week = ts(weekStart);
        List<AdminDtos.PayoutRow> payouts = jdbc.query("""
                        SELECT d.id, d.name, d.vehicle_plate, d.simulated,
                               COALESCE((SELECT SUM(l.amount) FROM driver_ledger l WHERE l.driver_id = d.id), 0) AS balance,
                               (SELECT COUNT(*) FROM ride r WHERE r.driver_id = d.id AND r.status = 'COMPLETED' AND r.completed_at >= ?) AS trips,
                               COALESCE((SELECT SUM(l.amount) FROM driver_ledger l WHERE l.driver_id = d.id AND l.created_at >= ?
                                         AND l.type IN ('EARNING', 'TIP', 'CANCEL_FEE', 'PENALTY', 'ADJUSTMENT')), 0) AS earned
                        FROM driver d WHERE d.status IN ('APPROVED', 'SUSPENDED')
                        ORDER BY d.simulated, balance DESC LIMIT 40
                        """,
                (rs, i) -> new AdminDtos.PayoutRow(rs.getLong("id"), rs.getString("name"), rs.getString("vehicle_plate"),
                        Money.of(rs.getBigDecimal("balance")), rs.getInt("trips"), Money.of(rs.getBigDecimal("earned")),
                        rs.getBoolean("simulated")),
                week, week);
        return new AdminDtos.Finance(days, totals, daily(days), byMethod, byType(days), payouts);
    }

    public AdminDtos.PageDto<AdminDtos.PaymentRow> paymentsPage(int page, int size) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM payment", Long.class);
        List<Object> args = new ArrayList<>();
        return new AdminDtos.PageDto<>(payments("", args, size, page * size), page, size, total == null ? 0 : total);
    }

    private List<AdminDtos.PaymentRow> payments(String where, List<?> args, int limit) {
        return payments(where, args, limit, 0);
    }

    private List<AdminDtos.PaymentRow> payments(String where, List<?> args, int limit, int offset) {
        List<Object> all = new ArrayList<>(args);
        all.add(limit);
        all.add(offset);
        return jdbc.query("""
                        SELECT p.id, p.ride_id, r.code, ri.name, p.kind, p.method, p.amount, p.status, p.reference, p.created_at
                        FROM payment p JOIN ride r ON r.id = p.ride_id JOIN rider ri ON ri.id = p.rider_id
                        """ + where + " ORDER BY p.id DESC LIMIT ? OFFSET ?",
                (rs, i) -> new AdminDtos.PaymentRow(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getBigDecimal(7), rs.getString(8), rs.getString(9),
                        instant(rs.getTimestamp(10))),
                all.toArray());
    }

    // ------------------------------------------------------------------ safety

    public List<AdminDtos.SosRow> sosRows(String status, int limit) {
        return status == null ? sosRows("", List.of(), limit) : sosRows(" WHERE s.status = ?", List.of(status), limit);
    }

    private List<AdminDtos.SosRow> sosRows(String where, List<?> args, int limit) {
        List<Object> all = new ArrayList<>(args);
        all.add(limit);
        return jdbc.query("""
                        SELECT s.id, s.ride_id, r.code, s.raised_by, ri.name, d.name, s.lat, s.lng, s.status, s.note,
                               s.created_at, s.resolved_at, s.resolved_by
                        FROM sos_alert s JOIN ride r ON r.id = s.ride_id JOIN rider ri ON ri.id = r.rider_id
                        LEFT JOIN driver d ON d.id = r.driver_id
                        """ + where + " ORDER BY s.id DESC LIMIT ?",
                (rs, i) -> new AdminDtos.SosRow(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), (Double) rs.getObject(7), (Double) rs.getObject(8),
                        rs.getString(9), rs.getString(10), instant(rs.getTimestamp(11)), instant(rs.getTimestamp(12)),
                        rs.getString(13)),
                all.toArray());
    }

    // ------------------------------------------------------------------ helpers

    private Instant startOf(LocalDate day) {
        return day.atStartOfDay(ClockConfig.CITY_ZONE).toInstant();
    }

    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static int num(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }

    private static BigDecimal money(Object value) {
        if (value == null) {
            return Money.ZERO;
        }
        return value instanceof BigDecimal bd ? Money.of(bd) : Money.of(((Number) value).doubleValue());
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10d;
    }

    private static String blank(String q) {
        return q == null || q.isBlank() ? null : q.trim();
    }

    /** Status counts for the people pages' filter chips. */
    public Map<String, Long> driverCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (DriverStatus s : DriverStatus.values()) {
            counts.put(s.name(), 0L);
        }
        jdbc.query("SELECT status, COUNT(*) FROM driver GROUP BY status", rs -> {
            counts.put(rs.getString(1), rs.getLong(2));
        });
        return counts;
    }

    public Map<String, Long> riderCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (RiderStatus s : RiderStatus.values()) {
            counts.put(s.name(), 0L);
        }
        jdbc.query("SELECT status, COUNT(*) FROM rider GROUP BY status", rs -> {
            counts.put(rs.getString(1), rs.getLong(2));
        });
        return counts;
    }
}
