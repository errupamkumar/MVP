package com.srmecotech.ecoride.driver;

import com.srmecotech.ecoride.common.config.ClockConfig;
import com.srmecotech.ecoride.common.error.BusinessRuleException;
import com.srmecotech.ecoride.common.error.InvalidRequestException;
import com.srmecotech.ecoride.common.error.NotFoundException;
import com.srmecotech.ecoride.common.security.AuthErrors;
import com.srmecotech.ecoride.common.security.JwtService;
import com.srmecotech.ecoride.common.security.Role;
import com.srmecotech.ecoride.common.util.Phones;
import com.srmecotech.ecoride.pricing.VehicleType;
import com.srmecotech.ecoride.pricing.VehicleTypeRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Driver onboarding (sign-up lands in PENDING_APPROVAL until an admin checks the licence and
 * vehicle), sign-in, profile and earnings. Driver accounts are separate from rider accounts.
 */
@Service
public class DriverAccountService {

    private final DriverRepository drivers;
    private final VehicleTypeRepository vehicleTypes;
    private final DriverLedgerService ledger;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public DriverAccountService(DriverRepository drivers, VehicleTypeRepository vehicleTypes, DriverLedgerService ledger,
                                PasswordEncoder passwordEncoder, JwtService jwt, JdbcTemplate jdbc, Clock clock) {
        this.drivers = drivers;
        this.vehicleTypes = vehicleTypes;
        this.ledger = ledger;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public DriverDtos.AuthResponse signup(DriverDtos.SignupRequest request) {
        String phone = Phones.normalise(request.phone());
        if (!phone.matches("[6-9]\\d{9}")) {
            throw new InvalidRequestException("PHONE_INVALID", "Enter a 10-digit Indian mobile number.");
        }
        VehicleType vt = vehicleTypes.findById(request.vehicleType().trim().toUpperCase(Locale.ROOT))
                .filter(VehicleType::isActive)
                .orElseThrow(() -> new InvalidRequestException("VEHICLE_TYPE_UNKNOWN", "Choose one of the vehicle types listed."));
        String plate = normalisePlate(request.vehiclePlate());
        if (!plate.matches("[A-Z]{2}\\d{1,2}[A-Z]{0,3}\\d{4}")) {
            throw new InvalidRequestException("PLATE_INVALID", "Enter the registration number as on the RC, e.g. MH12AB1234.");
        }
        String license = request.licenseNo().trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        String email = request.email() == null || request.email().isBlank() ? null : request.email().trim().toLowerCase(Locale.ROOT);
        if (drivers.existsByPhone(phone)) {
            throw new BusinessRuleException("PHONE_TAKEN", "A driver account with this mobile number already exists. Sign in instead.");
        }
        if (email != null && drivers.existsByEmailIgnoreCase(email)) {
            throw new BusinessRuleException("EMAIL_TAKEN", "A driver account with this email already exists. Sign in instead.");
        }
        if (drivers.existsByVehiclePlate(plate)) {
            throw new BusinessRuleException("PLATE_TAKEN", "This vehicle is already registered with another driver.");
        }
        Instant now = clock.instant();
        Driver driver = new Driver();
        driver.setName(request.name().trim());
        driver.setPhone(phone);
        driver.setEmail(email);
        driver.setPasswordHash(passwordEncoder.encode(request.password()));
        driver.setLicenseNo(license);
        driver.setVehicleType(vt.getCode());
        driver.setVehicleMake(request.vehicleMake().trim());
        driver.setVehicleModel(request.vehicleModel().trim());
        driver.setVehicleColor(request.vehicleColor().trim());
        driver.setVehiclePlate(plate);
        driver.setVehicleYear(request.vehicleYear());
        driver.setStatus(DriverStatus.PENDING_APPROVAL);
        driver.setAvailability(Availability.OFFLINE);
        driver.setRatingAvg(new BigDecimal("5.00"));
        driver.setRatingCount(0);
        driver.setCreatedAt(now);
        driver.setLastLoginAt(now);
        drivers.saveAndFlush(driver);
        return issue(driver);
    }

    @Transactional
    public DriverDtos.AuthResponse login(DriverDtos.LoginRequest request) {
        String login = request.login().trim();
        Driver driver = (Phones.looksLikePhone(login)
                ? drivers.findByPhone(Phones.normalise(login))
                : drivers.findByEmailIgnoreCase(login))
                .orElseThrow(AuthErrors::badCredentials);
        if (!passwordEncoder.matches(request.password(), driver.getPasswordHash())) {
            throw AuthErrors.badCredentials();
        }
        if (driver.getStatus() == DriverStatus.REJECTED) {
            throw AuthErrors.blocked("Your driver application was not approved"
                    + (driver.getStatusReason() == null ? "." : ": " + driver.getStatusReason()));
        }
        driver.setLastLoginAt(clock.instant());
        return issue(driver);
    }

    @Transactional(readOnly = true)
    public DriverDtos.Profile profile(long driverId) {
        return toProfile(drivers.findById(driverId).orElseThrow(() -> new NotFoundException("Driver", driverId)));
    }

    public DriverDtos.Profile toProfile(Driver d) {
        VehicleType vt = vehicleTypes.findById(d.getVehicleType()).orElseThrow();
        Integer acceptance = d.getOffersReceived() == 0 ? null
                : (int) Math.round(d.getOffersAccepted() * 100.0 / d.getOffersReceived());
        return new DriverDtos.Profile(d.getId(), d.getName(), d.getPhone(), d.getEmail(), d.getLicenseNo(), d.getStatus(),
                d.getStatusReason(), d.getAvailability(), d.getRatingAvg(), d.getRatingCount(), acceptance,
                new DriverDtos.Vehicle(vt.getCode(), vt.getName(), d.getVehicleMake(), d.getVehicleModel(),
                        d.getVehicleColor(), d.getVehiclePlate(), d.getVehicleYear()),
                d.getCreatedAt(), d.getApprovedAt());
    }

    /** Today and the last 7 days, in the city's time zone. */
    @Transactional(readOnly = true)
    public DriverDtos.Earnings earnings(long driverId) {
        LocalDate today = LocalDate.now(clock);
        Instant startToday = today.atStartOfDay(ClockConfig.CITY_ZONE).toInstant();
        Instant startWeek = today.minusDays(6).atStartOfDay(ClockConfig.CITY_ZONE).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(ClockConfig.CITY_ZONE).toInstant();

        Map<LocalDate, BigDecimal> netByDay = new HashMap<>();
        Map<LocalDate, Integer> tripsByDay = new HashMap<>();
        jdbc.query("""
                        SELECT created_at, amount FROM driver_ledger
                        WHERE driver_id = ? AND created_at >= ? AND created_at < ?
                          AND type IN ('EARNING', 'TIP', 'CANCEL_FEE', 'PENALTY', 'ADJUSTMENT')
                        """,
                rs -> {
                    LocalDate day = rs.getTimestamp(1).toInstant().atZone(ClockConfig.CITY_ZONE).toLocalDate();
                    netByDay.merge(day, rs.getBigDecimal(2), BigDecimal::add);
                },
                driverId, Timestamp.from(startWeek), Timestamp.from(end));
        jdbc.query("SELECT completed_at FROM ride WHERE driver_id = ? AND status = 'COMPLETED' AND completed_at >= ? AND completed_at < ?",
                rs -> {
                    LocalDate day = rs.getTimestamp(1).toInstant().atZone(ClockConfig.CITY_ZONE).toLocalDate();
                    tripsByDay.merge(day, 1, Integer::sum);
                },
                driverId, Timestamp.from(startWeek), Timestamp.from(end));
        List<DriverDtos.Day> days = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            days.add(new DriverDtos.Day(day, netByDay.getOrDefault(day, BigDecimal.ZERO).setScale(2),
                    tripsByDay.getOrDefault(day, 0)));
        }
        Driver driver = drivers.findById(driverId).orElseThrow();
        int onlineToday = driver.getOnlineSince() == null ? 0
                : (int) java.time.Duration.between(max(driver.getOnlineSince(), startToday), clock.instant()).toMinutes();
        return new DriverDtos.Earnings(
                period(driverId, startToday, end, onlineToday),
                period(driverId, startWeek, end, onlineToday),
                ledger.balance(driverId),
                days,
                ledger.lines(driverId, 30));
    }

    private DriverDtos.Period period(long driverId, Instant from, Instant to, int onlineMinutes) {
        BigDecimal fares = ledger.sum(driverId, from, to, DriverLedgerService.EARNING, DriverLedgerService.CANCEL_FEE);
        BigDecimal tips = ledger.sum(driverId, from, to, DriverLedgerService.TIP);
        BigDecimal penalties = ledger.sum(driverId, from, to, DriverLedgerService.PENALTY).negate();
        BigDecimal adjustments = ledger.sum(driverId, from, to, DriverLedgerService.ADJUSTMENT);
        BigDecimal cash = ledger.sum(driverId, from, to, DriverLedgerService.CASH_COLLECTED).negate();
        Map<String, Object> trips = jdbc.queryForMap("""
                SELECT COUNT(*) AS n, COALESCE(SUM(actual_distance_m), 0) AS m FROM ride
                WHERE driver_id = ? AND status = 'COMPLETED' AND completed_at >= ? AND completed_at < ?
                """, driverId, Timestamp.from(from), Timestamp.from(to));
        return new DriverDtos.Period(fares.add(tips).subtract(penalties).add(adjustments), fares, tips, penalties, cash,
                ((Number) trips.get("n")).intValue(), Math.round(((Number) trips.get("m")).doubleValue() / 100d) / 10d,
                onlineMinutes);
    }

    private static Instant max(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private DriverDtos.AuthResponse issue(Driver driver) {
        JwtService.IssuedToken token = jwt.issue(driver.getId(), Role.DRIVER, driver.getName());
        return new DriverDtos.AuthResponse(token.token(), token.expiresAt(), toProfile(driver));
    }

    public static String normalisePlate(String raw) {
        return raw == null ? "" : raw.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }
}
