package com.srmecotech.ecoride.demo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.srmecotech.ecoride.common.config.AppProperties;
import com.srmecotech.ecoride.common.config.ClockConfig;
import com.srmecotech.ecoride.common.settings.SettingsService;
import com.srmecotech.ecoride.common.util.Codes;
import com.srmecotech.ecoride.common.util.Money;
import com.srmecotech.ecoride.geo.GeoMath;
import com.srmecotech.ecoride.geo.GeoPoint;
import com.srmecotech.ecoride.geo.Polyline;
import com.srmecotech.ecoride.pricing.FareBreakdown;
import com.srmecotech.ecoride.pricing.FareCalculator;
import com.srmecotech.ecoride.pricing.VehicleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Builds the demo world on an empty database: settings, fare cards, Pune places, promos,
 * surge zones, the demo accounts, the simulated fleet, and {@code ecoride.demo.history-days}
 * of ride history (deterministic: the same seed every time, dates relative to now).
 */
@Component
public class DemoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);
    private static final double GST = 5;
    private static final double COMMISSION = 20;
    private static final int[] HOUR_WEIGHTS = {1, 1, 1, 1, 1, 2, 3, 5, 9, 10, 7, 5, 5, 5, 4, 4, 5, 7, 10, 10, 8, 5, 3, 2};

    private final JdbcTemplate jdbc;
    private final SettingsService settings;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;
    private final Clock clock;

    public DemoSeeder(JdbcTemplate jdbc, SettingsService settings, PasswordEncoder passwordEncoder,
                      ObjectMapper objectMapper, AppProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer admins = jdbc.queryForObject("SELECT COUNT(*) FROM admin_user", Integer.class);
        if (admins != null && admins == 0) {
            seed();
        }
    }

    public void seed() {
        long started = System.currentTimeMillis();
        Instant now = clock.instant();
        Timestamp nowTs = Timestamp.from(now);
        settings.seedDefaults();

        Map<String, VehicleType> products = new HashMap<>();
        for (DemoData.Product p : DemoData.PRODUCTS) {
            jdbc.update("""
                            INSERT INTO vehicle_type (code, name, description, capacity, base_fare, base_km, per_km, per_min,
                              min_fare, wait_free_min, wait_per_min, cancel_fee, sort_order, active, updated_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?)
                            """, p.code(), p.name(), p.description(), p.capacity(), p.baseFare(), p.baseKm(), p.perKm(),
                    p.perMin(), p.minFare(), p.waitFreeMin(), p.waitPerMin(), p.cancelFee(), p.sort(), nowTs);
            VehicleType vt = new VehicleType();
            vt.setCode(p.code());
            vt.setBaseFare(Money.of(p.baseFare()));
            vt.setBaseKm(BigDecimal.valueOf(p.baseKm()));
            vt.setPerKm(Money.of(p.perKm()));
            vt.setPerMin(Money.of(p.perMin()));
            vt.setMinFare(Money.of(p.minFare()));
            vt.setWaitFreeMin(p.waitFreeMin());
            vt.setWaitPerMin(Money.of(p.waitPerMin()));
            vt.setCancelFee(Money.of(p.cancelFee()));
            products.put(p.code(), vt);
        }
        jdbc.batchUpdate("INSERT INTO place (name, address, category, lat, lng) VALUES (?, ?, ?, ?, ?)",
                DemoData.PLACES.stream().map(p -> new Object[]{p.name(), p.address(), p.category(), p.lat(), p.lng()}).toList());

        String promoSql = """
                INSERT INTO promo_code (code, description, percent_off, flat_off, max_discount, min_fare, uses_per_rider,
                  valid_until, active, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        LocalDate today = LocalDate.now(clock);
        jdbc.update(promoSql, "FIRST50", "50% off your first ride (up to ₹75)", 50, null, 75, 0, 1, null, true, nowTs);
        jdbc.update(promoSql, "ECO20", "20% off (up to ₹60), 5 rides", 20, null, 60, 100, 5, java.sql.Date.valueOf(today.plusMonths(2)), true, nowTs);
        jdbc.update(promoSql, "WEEKEND25", "Flat ₹25 off rides above ₹150", null, 25, null, 150, 4, java.sql.Date.valueOf(today.plusMonths(1)), true, nowTs);
        jdbc.update(promoSql, "MONSOON40", "40% off (up to ₹80), monsoon special", 40, null, 80, 0, 2, java.sql.Date.valueOf(today.minusDays(5)), false, nowTs);

        String zoneSql = """
                INSERT INTO surge_zone (name, lat, lng, radius_m, mode, manual_multiplier, active, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbc.update(zoneSql, "Hinjewadi IT Park", 18.5900, 73.7300, 3000, "AUTO", 1.0, true, nowTs);
        jdbc.update(zoneSql, "Viman Nagar · Airport", 18.5720, 73.9150, 2500, "AUTO", 1.0, true, nowTs);
        jdbc.update(zoneSql, "Pune Station · Camp", 18.5260, 73.8760, 1800, "AUTO", 1.0, true, nowTs);
        jdbc.update(zoneSql, "Koregaon Park (events)", 18.5366, 73.8933, 1500, "MANUAL", 1.3, false, nowTs);

        jdbc.update("INSERT INTO admin_user (name, email, password_hash, created_at) VALUES (?, ?, ?, ?)",
                DemoData.ADMIN.name(), DemoData.ADMIN.email(), passwordEncoder.encode(DemoData.ADMIN_PASSWORD),
                Timestamp.from(now.minus(Duration.ofDays(120))));

        // ------------------------------------------------------------------ riders
        String riderSql = """
                INSERT INTO rider (name, phone, email, password_hash, status, rating_avg, rating_count, wallet_balance,
                  simulated, created_at, last_login_at) VALUES (?, ?, ?, ?, 'ACTIVE', 5.00, 0, ?, ?, ?, ?)
                """;
        String riderHash = passwordEncoder.encode(DemoData.RIDER_PASSWORD);
        String simulatedHash = passwordEncoder.encode(DemoData.SIMULATED_PASSWORD);
        jdbc.update(riderSql, DemoData.DEMO_RIDER.name(), DemoData.DEMO_RIDER.phone(), DemoData.DEMO_RIDER.email(),
                riderHash, 500, false, Timestamp.from(now.minus(Duration.ofDays(75))), nowTs);
        long demoRiderId = id("SELECT id FROM rider WHERE phone = ?", DemoData.DEMO_RIDER.phone());
        jdbc.update(riderSql, DemoData.SECOND_RIDER.name(), DemoData.SECOND_RIDER.phone(), DemoData.SECOND_RIDER.email(),
                riderHash, 250, false, Timestamp.from(now.minus(Duration.ofDays(40))), nowTs);
        long secondRiderId = id("SELECT id FROM rider WHERE phone = ?", DemoData.SECOND_RIDER.phone());
        List<Long> simRiders = new ArrayList<>();
        for (int i = 0; i < DemoData.SIMULATED_RIDER_NAMES.size(); i++) {
            String phone = "92000%05d".formatted(i + 1);
            jdbc.update(riderSql, DemoData.SIMULATED_RIDER_NAMES.get(i), phone, null, simulatedHash, 5000, true,
                    Timestamp.from(now.minus(Duration.ofDays(60 + i))), null);
            simRiders.add(id("SELECT id FROM rider WHERE phone = ?", phone));
        }
        String walletSql = """
                INSERT INTO wallet_txn (rider_id, ride_id, type, amount, balance_after, note, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        jdbc.update(walletSql, demoRiderId, null, "TOPUP", 500, 500, "Added via UPI (simulated)",
                Timestamp.from(now.minus(Duration.ofDays(3))));
        jdbc.update(walletSql, secondRiderId, null, "TOPUP", 250, 250, "Welcome credit",
                Timestamp.from(now.minus(Duration.ofDays(40))));
        String placeSql = "INSERT INTO saved_place (rider_id, label, name, address, lat, lng, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        jdbc.update(placeSql, demoRiderId, "HOME", "Home", "Baner Road, Baner, Pune", 18.5590, 73.7845, nowTs);
        jdbc.update(placeSql, demoRiderId, "WORK", "Work", "Hinjewadi Phase 1, Rajiv Gandhi Infotech Park", 18.5917, 73.7348, nowTs);

        // ------------------------------------------------------------------ drivers
        String driverHash = passwordEncoder.encode(DemoData.DRIVER_PASSWORD);
        long demoDriverId = insertDriver(DemoData.DEMO_DRIVER, driverHash, "APPROVED", null, "OFFLINE", false,
                now.minus(Duration.ofDays(200)), now.minus(Duration.ofHours(3)));
        insertDriver(DemoData.PENDING_DRIVER, driverHash, "PENDING_APPROVAL", null, "OFFLINE", false,
                now.minus(Duration.ofHours(5)), null);
        insertDriver(DemoData.SUSPENDED_DRIVER, driverHash, "SUSPENDED", "Vehicle insurance expired on "
                + today.minusDays(4) + ". Upload the renewed policy.", "OFFLINE", false, now.minus(Duration.ofDays(300)), now.minus(Duration.ofDays(5)));
        Random rnd = new Random(20261009L);
        Map<String, List<Long>> botsByType = new HashMap<>();
        for (DemoData.DriverSeed seed : DemoData.SIMULATED_DRIVERS) {
            long id = insertDriver(seed, simulatedHash, "APPROVED", null, "ONLINE", true,
                    now.minus(Duration.ofDays(100 + rnd.nextInt(400))), now);
            botsByType.computeIfAbsent(seed.type(), k -> new ArrayList<>()).add(id);
            jdbc.update("UPDATE driver SET online_since = ? WHERE id = ?",
                    Timestamp.from(now.minus(Duration.ofMinutes(20 + rnd.nextInt(240)))), id);
        }

        int rides = seedHistory(rnd, products, botsByType, demoDriverId, demoRiderId, secondRiderId, simRiders);
        log.info("Demo data ready in {} ms: {} riders, {} drivers, {} rides of history",
                System.currentTimeMillis() - started, simRiders.size() + 2, DemoData.SIMULATED_DRIVERS.size() + 3, rides);
    }

    private long insertDriver(DemoData.DriverSeed s, String hash, String status, String reason, String availability,
                              boolean simulated, Instant created, Instant locatedAt) {
        jdbc.update("""
                        INSERT INTO driver (name, phone, email, password_hash, license_no, vehicle_type, vehicle_make,
                          vehicle_model, vehicle_color, vehicle_plate, vehicle_year, status, status_reason, availability,
                          rating_avg, rating_count, offers_received, offers_accepted, lat, lng, heading, speed_kmh,
                          located_at, simulated, created_at, approved_at, last_login_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 5.00, 0, 0, 0, ?, ?, ?, 0, ?, ?, ?, ?, ?)
                        """,
                s.name(), s.phone(), simulated ? null : s.name().toLowerCase().replace(' ', '.') + "@example.com", hash,
                s.license(), s.type(), s.make(), s.model(), s.color(), s.plate(), s.year(), status, reason, availability,
                locatedAt == null ? null : s.hubLat(), locatedAt == null ? null : s.hubLng(),
                locatedAt == null ? null : (int) (Math.abs(s.plate().hashCode()) % 360),
                locatedAt == null ? null : Timestamp.from(locatedAt), simulated, Timestamp.from(created),
                "APPROVED".equals(status) || "SUSPENDED".equals(status) ? Timestamp.from(created.plus(Duration.ofDays(2))) : null,
                locatedAt == null ? null : Timestamp.from(locatedAt));
        return id("SELECT id FROM driver WHERE phone = ?", s.phone());
    }

    private int seedHistory(Random rnd, Map<String, VehicleType> products, Map<String, List<Long>> botsByType,
                            long demoDriverId, long demoRiderId, long secondRiderId, List<Long> simRiders) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock);
        int days = properties.demo().historyDays();
        List<Object[]> rides = new ArrayList<>();
        List<Object[]> events = new ArrayList<>();
        List<Object[]> offers = new ArrayList<>();
        List<Object[]> payments = new ArrayList<>();
        List<Object[]> ledger = new ArrayList<>();
        List<Object[]> wallet = new ArrayList<>();
        Map<Long, BigDecimal> walletBalance = new HashMap<>();
        for (Long id : simRiders) {
            walletBalance.put(id, Money.of(6000));
            wallet.add(new Object[]{id, null, "TOPUP", 6000, 6000, "Added via UPI (simulated)",
                    Timestamp.from(today.minusDays(days + 1L).atStartOfDay(ClockConfig.CITY_ZONE).toInstant())});
        }
        String[] types = {"MINI", "MINI", "MINI", "MINI", "AUTO", "AUTO", "BIKE", "BIKE", "SEDAN", "SEDAN", "SUV"};
        long rideId = 0;
        for (int d = days; d >= 0; d--) {
            LocalDate date = today.minusDays(d);
            boolean weekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
            int count = (weekend ? 16 : 24) + rnd.nextInt(10);
            for (int i = 0; i < count; i++) {
                int hour = pickHour(rnd);
                Instant requested = date.atTime(LocalTime.of(hour, rnd.nextInt(60), rnd.nextInt(60)))
                        .atZone(ClockConfig.CITY_ZONE).toInstant();
                if (requested.isAfter(now.minus(Duration.ofMinutes(45)))) {
                    continue;
                }
                String type = types[rnd.nextInt(types.length)];
                VehicleType vt = products.get(type);
                long riderId;
                double who = rnd.nextDouble();
                if (who < 0.025) {
                    riderId = demoRiderId;
                } else if (who < 0.035) {
                    riderId = secondRiderId;
                } else {
                    riderId = simRiders.get(rnd.nextInt(simRiders.size()));
                }
                boolean simulatedRider = riderId != demoRiderId && riderId != secondRiderId;
                List<Long> pool = botsByType.get(type);
                long driverId = "MINI".equals(type) && rnd.nextDouble() < 0.22 ? demoDriverId : pool.get(rnd.nextInt(pool.size()));

                DemoData.Place from;
                DemoData.Place to;
                double straight;
                do {
                    from = DemoData.PLACES.get(rnd.nextInt(DemoData.PLACES.size()));
                    to = DemoData.PLACES.get(rnd.nextInt(DemoData.PLACES.size()));
                    straight = GeoMath.distanceM(from.point(), to.point());
                } while (straight < 1500 || straight > (type.equals("BIKE") || type.equals("AUTO") ? 11000 : 19000));
                int distM = (int) Math.round(straight * (1.25 + rnd.nextDouble() * 0.12));
                int durS = (int) Math.round(distM / (21 / 3.6) * (0.85 + rnd.nextDouble() * 0.4));
                boolean peak = hour == 8 || hour == 9 || hour == 18 || hour == 19 || hour == 20;
                BigDecimal surge = peak && rnd.nextDouble() < 0.35
                        ? BigDecimal.valueOf(1.1 + rnd.nextInt(5) * 0.1).setScale(2, java.math.RoundingMode.HALF_UP)
                        : BigDecimal.ONE.setScale(2, java.math.RoundingMode.HALF_UP);
                FareCalculator.Promo promo = rnd.nextDouble() < 0.06
                        ? new FareCalculator.Promo("ECO20", 20, null, Money.of(60)) : null;
                double m = rnd.nextDouble();
                String method = m < 0.45 ? "UPI" : m < 0.80 || !simulatedRider ? "CASH" : "WALLET";
                if (!simulatedRider && m >= 0.45) {
                    method = rnd.nextBoolean() ? "UPI" : "CASH";
                }
                FareBreakdown quote = FareCalculator.calculate(vt, distM, durS, surge, 0, promo, GST);
                double outcome = rnd.nextDouble();
                rideId++;
                String code = Codes.rideCode();
                String route = Polyline.encode(List.of(from.point(), to.point()));
                if (outcome < 0.86) {
                    Instant accepted = requested.plusSeconds(15 + rnd.nextInt(65));
                    Instant arrived = accepted.plusSeconds(120 + rnd.nextInt(420));
                    Instant start = arrived.plusSeconds(30 + rnd.nextInt(200));
                    Instant completed = start.plusSeconds(durS);
                    if (completed.isAfter(now.minus(Duration.ofMinutes(5)))) {
                        rideId--;
                        continue;
                    }
                    int waitS = (int) Duration.between(arrived, start).toSeconds();
                    FareBreakdown fare = FareCalculator.calculate(vt, distM, durS, surge, waitS, promo, GST);
                    BigDecimal commission = Money.percent(fare.fareBeforeDiscount(), COMMISSION);
                    BigDecimal earning = Money.of(fare.fareBeforeDiscount().subtract(commission));
                    int actual = (int) Math.round(distM * (0.96 + rnd.nextDouble() * 0.08));
                    int pickupTravel = (int) Math.round(Duration.between(accepted, arrived).toSeconds() * 5.5);
                    double r = rnd.nextDouble();
                    int driverRating = r < 0.72 ? 5 : r < 0.94 ? 4 : r < 0.99 ? 3 : 2;
                    String feedback = driverRating == 5 && rnd.nextDouble() < 0.4
                            ? List.of("Great driving", "Clean car", "Polite", "On time", "Smooth ride").get(rnd.nextInt(5))
                            : driverRating <= 3 ? List.of("Late pickup", "Took a longer route", "AC not working").get(rnd.nextInt(3)) : null;
                    int riderRating = rnd.nextDouble() < 0.82 ? 5 : 4;
                    BigDecimal tip = rnd.nextDouble() < 0.12 ? Money.of(List.of(10, 20, 30, 50).get(rnd.nextInt(4))) : Money.ZERO;
                    rides.add(rideRow(rideId, code, riderId, driverId, type, "COMPLETED", from, to, distM, durS, route,
                            surge, promo, quote, method, "PAID", requested, accepted, arrived, start, completed, null,
                            null, null, Money.ZERO, actual, pickupTravel, fare, commission, earning, tip, driverRating,
                            feedback, riderRating, simulatedRider));
                    offers.add(new Object[]{rideId, driverId, "ACCEPTED", 600 + rnd.nextInt(2500), 120 + rnd.nextInt(400),
                            ts(requested.plusSeconds(2)), ts(requested.plusSeconds(17)), ts(accepted)});
                    events.add(event(rideId, "REQUESTED", "RIDER", "Requested %s: %.1f km, upfront %s".formatted(type, distM / 1000d, Money.inr(quote.total())), requested));
                    events.add(event(rideId, "ACCEPTED", "DRIVER", "Accepted by the driver", accepted));
                    events.add(event(rideId, "ARRIVED", "DRIVER", "Driver arrived at the pickup", arrived));
                    events.add(event(rideId, "STARTED", "DRIVER", "Code verified, trip started", start));
                    events.add(event(rideId, "COMPLETED", "DRIVER", "Trip completed: %.2f km by GPS".formatted(actual / 1000d), completed));
                    events.add(event(rideId, "FARE", "SYSTEM", "Upfront fare %s stands".formatted(Money.inr(fare.total())), completed));
                    payments.add(new Object[]{rideId, riderId, "RIDE", method, fare.total(), "PAID",
                            "CASH".equals(method) ? null : Codes.reference(method.equals("UPI") ? "UPI" : "WAL"), ts(completed), ts(completed)});
                    ledger.add(new Object[]{driverId, rideId, "EARNING", earning, "Ride " + code, ts(completed)});
                    if ("CASH".equals(method)) {
                        ledger.add(new Object[]{driverId, rideId, "CASH_COLLECTED", fare.total().negate(), "Cash collected on " + code, ts(completed)});
                    }
                    if (tip.signum() > 0) {
                        ledger.add(new Object[]{driverId, rideId, "TIP", tip, "Tip on ride " + code, ts(completed.plusSeconds(60))});
                        payments.add(new Object[]{rideId, riderId, "TIP", "CASH".equals(method) ? "UPI" : method, tip, "PAID",
                                Codes.reference("UPI"), ts(completed.plusSeconds(60)), ts(completed.plusSeconds(60))});
                    }
                    if ("WALLET".equals(method)) {
                        BigDecimal after = walletBalance.get(riderId).subtract(fare.total());
                        walletBalance.put(riderId, after);
                        wallet.add(new Object[]{riderId, rideId, "RIDE", fare.total().negate(), after, "Fare " + code, ts(completed)});
                    }
                } else if (outcome < 0.93) {
                    boolean afterAccept = rnd.nextDouble() < 0.4;
                    Instant accepted = afterAccept ? requested.plusSeconds(15 + rnd.nextInt(60)) : null;
                    Instant cancelled = afterAccept ? accepted.plusSeconds(30 + rnd.nextInt(280)) : requested.plusSeconds(20 + rnd.nextInt(60));
                    boolean fee = afterAccept && Duration.between(accepted, cancelled).toSeconds() > 120;
                    BigDecimal cancelFee = fee ? Money.of(vt.getCancelFee()) : Money.ZERO;
                    rides.add(rideRow(rideId, code, riderId, afterAccept ? driverId : null, type, "CANCELLED", from, to,
                            distM, durS, route, surge, promo, quote, method, fee ? "PAID" : "PENDING", requested, accepted,
                            null, null, null, cancelled, "RIDER",
                            List.of("Changed my plans", "Driver was too far", "Booked by mistake", "Found another ride").get(rnd.nextInt(4)),
                            cancelFee, 0, 0, null, null, null, Money.ZERO, null, null, null, simulatedRider));
                    events.add(event(rideId, "REQUESTED", "RIDER", "Requested " + type, requested));
                    if (afterAccept) {
                        offers.add(new Object[]{rideId, driverId, "ACCEPTED", 800 + rnd.nextInt(2500), 150 + rnd.nextInt(400),
                                ts(requested.plusSeconds(2)), ts(requested.plusSeconds(17)), ts(accepted)});
                        events.add(event(rideId, "ACCEPTED", "DRIVER", "Accepted by the driver", accepted));
                    }
                    events.add(event(rideId, "CANCELLED", "RIDER", fee ? "Rider cancelled, cancellation fee " + Money.inr(cancelFee)
                            : "Rider cancelled, no charge", cancelled));
                    if (fee) {
                        payments.add(new Object[]{rideId, riderId, "CANCEL_FEE", "CASH".equals(method) ? "UPI" : method, cancelFee,
                                "PAID", Codes.reference("UPI"), ts(cancelled), ts(cancelled)});
                        ledger.add(new Object[]{driverId, rideId, "CANCEL_FEE",
                                Money.of(cancelFee.subtract(Money.percent(cancelFee, COMMISSION))), "Cancellation fee share, ride " + code, ts(cancelled)});
                    }
                } else {
                    Instant ended = requested.plusSeconds(120);
                    rides.add(rideRow(rideId, code, riderId, null, type, "NO_DRIVERS", from, to, distM, durS, route,
                            surge, promo, quote, method, "PENDING", requested, null, null, null, null, ended, "SYSTEM",
                            "No driver accepted within 120 s", Money.ZERO, 0, 0, null, null, null, Money.ZERO, null, null,
                            null, simulatedRider));
                    events.add(event(rideId, "REQUESTED", "RIDER", "Requested " + type, requested));
                    events.add(event(rideId, "NO_DRIVERS", "SYSTEM", "No driver accepted. The rider was told no cars are available.", ended));
                }
            }
        }

        jdbc.batchUpdate("""
                INSERT INTO ride (id, code, rider_id, driver_id, vehicle_type, status, pickup_lat, pickup_lng, pickup_address,
                  drop_lat, drop_lng, drop_address, est_distance_m, est_duration_s, route_polyline, route_provider,
                  surge_multiplier, promo_code, quote, est_fare, payment_method, payment_status, otp, requested_at,
                  search_started_at, accepted_at, arrived_at, started_at, completed_at, cancelled_at, cancelled_by,
                  cancel_reason, cancellation_fee, actual_distance_m, pickup_travel_m, fare, fare_total, fare_policy, tax,
                  discount, commission, driver_earning, tip, driver_rating, driver_feedback, rider_rating, simulated)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                        ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, rides);
        jdbc.batchUpdate("INSERT INTO ride_event (ride_id, type, actor, message, created_at) VALUES (?, ?, ?, ?, ?)", events);
        jdbc.batchUpdate("""
                INSERT INTO ride_offer (ride_id, driver_id, status, distance_m, eta_s, offered_at, expires_at, responded_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, offers);
        jdbc.batchUpdate("""
                INSERT INTO payment (ride_id, rider_id, kind, method, amount, status, reference, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, payments);
        jdbc.batchUpdate("INSERT INTO driver_ledger (driver_id, ride_id, type, amount, note, created_at) VALUES (?, ?, ?, ?, ?, ?)", ledger);
        jdbc.batchUpdate("""
                INSERT INTO wallet_txn (rider_id, ride_id, type, amount, balance_after, note, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, wallet);
        walletBalance.forEach((id, balance) -> jdbc.update("UPDATE rider SET wallet_balance = ? WHERE id = ?", balance, id));

        // Weekly payouts every Monday: what is owed now is only this week's work.
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Timestamp payoutAt = ts(monday.atTime(10, 0).atZone(ClockConfig.CITY_ZONE).toInstant());
        Timestamp cutoff = ts(monday.atStartOfDay(ClockConfig.CITY_ZONE).toInstant());
        jdbc.update("""
                INSERT INTO driver_ledger (driver_id, ride_id, type, amount, note, created_at)
                SELECT driver_id, NULL, 'PAYOUT', -SUM(amount), 'Weekly payout', ? FROM driver_ledger
                WHERE created_at < ? GROUP BY driver_id HAVING SUM(amount) > 0
                """, payoutAt, cutoff);

        jdbc.update("""
                UPDATE driver d SET
                  rating_avg = COALESCE((SELECT ROUND(AVG(r.driver_rating), 2) FROM ride r WHERE r.driver_id = d.id AND r.driver_rating IS NOT NULL), 5.00),
                  rating_count = (SELECT COUNT(r.driver_rating) FROM ride r WHERE r.driver_id = d.id),
                  offers_accepted = (SELECT COUNT(*) FROM ride_offer o WHERE o.driver_id = d.id AND o.status = 'ACCEPTED'),
                  offers_received = ROUND((SELECT COUNT(*) FROM ride_offer o WHERE o.driver_id = d.id) * 1.12)
                """);
        jdbc.update("""
                UPDATE rider u SET
                  rating_avg = COALESCE((SELECT ROUND(AVG(r.rider_rating), 2) FROM ride r WHERE r.rider_id = u.id AND r.rider_rating IS NOT NULL), 5.00),
                  rating_count = (SELECT COUNT(r.rider_rating) FROM ride r WHERE r.rider_id = u.id)
                """);
        return rides.size();
    }

    private Object[] rideRow(long id, String code, long riderId, Long driverId, String type, String status,
                             DemoData.Place from, DemoData.Place to, int distM, int durS, String route, BigDecimal surge,
                             FareCalculator.Promo promo, FareBreakdown quote, String method, String paymentStatus,
                             Instant requested, Instant accepted, Instant arrived, Instant started, Instant completed,
                             Instant cancelled, String cancelledBy, String cancelReason, BigDecimal cancelFee, int actual,
                             int pickupTravel, FareBreakdown fare, BigDecimal commission, BigDecimal earning,
                             BigDecimal tip, Integer driverRating, String feedback, Integer riderRating, boolean simulated) {
        return new Object[]{id, code, riderId, driverId, type, status, from.lat(), from.lng(), from.name() + ", " + from.address(),
                to.lat(), to.lng(), to.name() + ", " + to.address(), distM, durS, route, "STRAIGHT", surge,
                promo == null ? null : promo.code(), json(quote), quote.total(), method, paymentStatus, Codes.otp(),
                ts(requested), ts(requested), ts(accepted), ts(arrived), ts(started), ts(completed), ts(cancelled),
                cancelledBy, cancelReason, cancelFee, actual, pickupTravel, fare == null ? null : json(fare),
                fare == null ? null : fare.total(), fare == null ? null : "UPFRONT", fare == null ? null : fare.tax(),
                fare == null ? null : fare.discount(), commission, earning, tip, driverRating, feedback, riderRating,
                simulated};
    }

    private static Object[] event(long rideId, String type, String actor, String message, Instant at) {
        return new Object[]{rideId, type, actor, message, ts(at)};
    }

    private static int pickHour(Random rnd) {
        int total = 0;
        for (int w : HOUR_WEIGHTS) {
            total += w;
        }
        int pick = rnd.nextInt(total);
        for (int h = 0; h < HOUR_WEIGHTS.length; h++) {
            pick -= HOUR_WEIGHTS[h];
            if (pick < 0) {
                return h;
            }
        }
        return 12;
    }

    private String json(FareBreakdown fare) {
        try {
            return objectMapper.writeValueAsString(fare);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private long id(String sql, Object arg) {
        Long id = jdbc.queryForObject(sql, Long.class, arg);
        return id == null ? 0 : id;
    }

    private static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    /** Places within reach of a point, for the simulated riders' requests. */
    static List<DemoData.Place> placesNear(GeoPoint point, double minM, double maxM) {
        return DemoData.PLACES.stream()
                .filter(p -> {
                    double d = GeoMath.distanceM(point, p.point());
                    return d >= minM && d <= maxM;
                })
                .toList();
    }
}
