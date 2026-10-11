package com.srmecotech.ecoride.admin;

import com.srmecotech.ecoride.common.audit.AuditService;
import com.srmecotech.ecoride.common.error.BusinessRuleException;
import com.srmecotech.ecoride.common.error.InvalidRequestException;
import com.srmecotech.ecoride.common.error.NotFoundException;
import com.srmecotech.ecoride.common.security.AuthErrors;
import com.srmecotech.ecoride.common.security.AuthenticatedUser;
import com.srmecotech.ecoride.common.security.JwtService;
import com.srmecotech.ecoride.common.security.Role;
import com.srmecotech.ecoride.common.settings.Setting;
import com.srmecotech.ecoride.common.settings.SettingsService;
import com.srmecotech.ecoride.common.util.Codes;
import com.srmecotech.ecoride.common.util.Money;
import com.srmecotech.ecoride.driver.Availability;
import com.srmecotech.ecoride.driver.Driver;
import com.srmecotech.ecoride.driver.DriverLedgerService;
import com.srmecotech.ecoride.driver.DriverRepository;
import com.srmecotech.ecoride.driver.DriverStatus;
import com.srmecotech.ecoride.pricing.PromoCode;
import com.srmecotech.ecoride.pricing.PromoCodeRepository;
import com.srmecotech.ecoride.pricing.SurgeZone;
import com.srmecotech.ecoride.pricing.SurgeZoneRepository;
import com.srmecotech.ecoride.pricing.VehicleType;
import com.srmecotech.ecoride.pricing.VehicleTypeRepository;
import com.srmecotech.ecoride.ride.DispatchScheduler;
import com.srmecotech.ecoride.ride.Payment;
import com.srmecotech.ecoride.ride.PaymentMethod;
import com.srmecotech.ecoride.ride.PaymentRepository;
import com.srmecotech.ecoride.ride.PaymentStatus;
import com.srmecotech.ecoride.ride.Ride;
import com.srmecotech.ecoride.ride.RideLifecycleService;
import com.srmecotech.ecoride.ride.RideLog;
import com.srmecotech.ecoride.ride.RideOfferRepository;
import com.srmecotech.ecoride.ride.RideRepository;
import com.srmecotech.ecoride.ride.SosAlert;
import com.srmecotech.ecoride.ride.SosAlertRepository;
import com.srmecotech.ecoride.rider.Rider;
import com.srmecotech.ecoride.rider.RiderRepository;
import com.srmecotech.ecoride.rider.RiderStatus;
import com.srmecotech.ecoride.rider.WalletService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

/** Write side of the admin console. Every change is written to the audit log. */
@Service
public class AdminCommandService {

    private final AdminUserRepository admins;
    private final DriverRepository drivers;
    private final RiderRepository riders;
    private final RideRepository rides;
    private final RideOfferRepository offers;
    private final VehicleTypeRepository vehicleTypes;
    private final SurgeZoneRepository zones;
    private final PromoCodeRepository promos;
    private final PaymentRepository payments;
    private final SosAlertRepository sosAlerts;
    private final RideLifecycleService lifecycle;
    private final DriverLedgerService ledger;
    private final WalletService wallet;
    private final SettingsService settings;
    private final DispatchScheduler dispatcher;
    private final AuditService audit;
    private final RideLog rideLog;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;
    private final Clock clock;

    public AdminCommandService(AdminUserRepository admins, DriverRepository drivers, RiderRepository riders,
                               RideRepository rides, RideOfferRepository offers, VehicleTypeRepository vehicleTypes,
                               SurgeZoneRepository zones, PromoCodeRepository promos, PaymentRepository payments,
                               SosAlertRepository sosAlerts, RideLifecycleService lifecycle, DriverLedgerService ledger,
                               WalletService wallet, SettingsService settings, DispatchScheduler dispatcher,
                               AuditService audit, RideLog rideLog, PasswordEncoder passwordEncoder, JwtService jwt,
                               Clock clock) {
        this.admins = admins;
        this.drivers = drivers;
        this.riders = riders;
        this.rides = rides;
        this.offers = offers;
        this.vehicleTypes = vehicleTypes;
        this.zones = zones;
        this.promos = promos;
        this.payments = payments;
        this.sosAlerts = sosAlerts;
        this.lifecycle = lifecycle;
        this.ledger = ledger;
        this.wallet = wallet;
        this.settings = settings;
        this.dispatcher = dispatcher;
        this.audit = audit;
        this.rideLog = rideLog;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
        this.clock = clock;
    }

    @Transactional
    public AdminDtos.AuthResponse login(AdminDtos.LoginRequest request) {
        AdminUser admin = admins.findByEmailIgnoreCase(request.email().trim()).orElseThrow(AuthErrors::badCredentials);
        if (!passwordEncoder.matches(request.password(), admin.getPasswordHash())) {
            throw AuthErrors.badCredentials();
        }
        admin.setLastLoginAt(clock.instant());
        JwtService.IssuedToken token = jwt.issue(admin.getId(), Role.ADMIN, admin.getName());
        return new AdminDtos.AuthResponse(token.token(), token.expiresAt(),
                new AdminDtos.AdminProfile(admin.getId(), admin.getName(), admin.getEmail()));
    }

    public AdminDtos.AdminProfile me(long adminId) {
        AdminUser admin = admins.findById(adminId).orElseThrow(() -> new NotFoundException("Admin", adminId));
        return new AdminDtos.AdminProfile(admin.getId(), admin.getName(), admin.getEmail());
    }

    // ------------------------------------------------------------------ drivers

    @Transactional
    public void approveDriver(AuthenticatedUser admin, long driverId) {
        Driver d = driver(driverId);
        if (d.getStatus() == DriverStatus.APPROVED) {
            return;
        }
        d.setStatus(DriverStatus.APPROVED);
        d.setStatusReason(null);
        if (d.getApprovedAt() == null) {
            d.setApprovedAt(clock.instant());
        }
        drivers.save(d);
        audit.record(admin, "DRIVER_APPROVED", driverTarget(d), "Licence " + d.getLicenseNo() + ", " + d.vehicleLabel());
    }

    @Transactional
    public void rejectDriver(AuthenticatedUser admin, long driverId, String reason) {
        Driver d = driver(driverId);
        if (d.getAvailability() != Availability.OFFLINE) {
            throw new BusinessRuleException("DRIVER_ONLINE", "Suspend an active driver instead of rejecting them.");
        }
        d.setStatus(DriverStatus.REJECTED);
        d.setStatusReason(reason);
        drivers.save(d);
        audit.record(admin, "DRIVER_REJECTED", driverTarget(d), reason);
    }

    @Transactional
    public void suspendDriver(AuthenticatedUser admin, long driverId, String reason) {
        Driver d = driver(driverId);
        if (d.getAvailability() == Availability.ON_TRIP) {
            throw new BusinessRuleException("DRIVER_ON_TRIP", d.getName() + " is on a ride. Suspend after it ends, or cancel the ride first.");
        }
        drivers.setAvailability(driverId, Availability.OFFLINE, null);
        offers.cancelPendingForDriver(driverId, null, clock.instant());
        d = driver(driverId);
        d.setStatus(DriverStatus.SUSPENDED);
        d.setStatusReason(reason);
        drivers.save(d);
        dispatcher.kick();
        audit.record(admin, "DRIVER_SUSPENDED", driverTarget(d), reason);
    }

    @Transactional
    public void reactivateDriver(AuthenticatedUser admin, long driverId) {
        Driver d = driver(driverId);
        d.setStatus(DriverStatus.APPROVED);
        d.setStatusReason(null);
        if (d.getApprovedAt() == null) {
            d.setApprovedAt(clock.instant());
        }
        drivers.save(d);
        audit.record(admin, "DRIVER_REACTIVATED", driverTarget(d), "Back to approved");
    }

    @Transactional
    public void forceOffline(AuthenticatedUser admin, long driverId) {
        Driver d = driver(driverId);
        if (d.getAvailability() == Availability.ON_TRIP) {
            throw new BusinessRuleException("DRIVER_ON_TRIP", d.getName() + " is on a ride and cannot be taken offline now.");
        }
        lifecycle.goOffline(driverId);
        audit.record(admin, "DRIVER_FORCED_OFFLINE", driverTarget(d), "Taken offline from the console");
    }

    /** Pays out the driver's balance (simulated bank transfer). */
    @Transactional
    public BigDecimal payout(AuthenticatedUser admin, long driverId) {
        Driver d = driver(driverId);
        BigDecimal balance = ledger.balance(driverId);
        if (balance.signum() <= 0) {
            throw new BusinessRuleException("NOTHING_TO_PAY", d.getName() + " has no balance to pay out (" + Money.inr(balance) + ").");
        }
        String reference = Codes.reference("NEFT");
        ledger.add(driverId, null, DriverLedgerService.PAYOUT, balance.negate(), "Payout " + reference);
        audit.record(admin, "DRIVER_PAYOUT", driverTarget(d), Money.inr(balance) + " ref " + reference);
        return balance;
    }

    // ------------------------------------------------------------------ riders

    @Transactional
    public void blockRider(AuthenticatedUser admin, long riderId, String reason) {
        Rider r = rider(riderId);
        r.setStatus(RiderStatus.BLOCKED);
        r.setStatusReason(reason);
        riders.save(r);
        audit.record(admin, "RIDER_BLOCKED", riderTarget(r), reason);
    }

    @Transactional
    public void unblockRider(AuthenticatedUser admin, long riderId) {
        Rider r = rider(riderId);
        r.setStatus(RiderStatus.ACTIVE);
        r.setStatusReason(null);
        riders.save(r);
        audit.record(admin, "RIDER_UNBLOCKED", riderTarget(r), "Access restored");
    }

    @Transactional
    public void creditWallet(AuthenticatedUser admin, long riderId, BigDecimal amount, String note) {
        Rider r = rider(riderId);
        wallet.credit(riderId, null, "ADJUSTMENT", amount, note);
        audit.record(admin, "WALLET_CREDIT", riderTarget(r), Money.inr(Money.of(amount)) + ": " + note);
    }

    // ------------------------------------------------------------------ rides

    @Transactional
    public void cancelRide(AuthenticatedUser admin, long rideId, String reason) {
        Ride ride = lifecycle.adminCancel(rideId, admin.name(), reason);
        audit.record(admin, "RIDE_CANCELLED", "Ride " + ride.getCode(), reason);
    }

    /** Credits the rider's wallet, whatever the original payment method. */
    @Transactional
    public void refund(AuthenticatedUser admin, long rideId, BigDecimal amount, String note) {
        Ride ride = rides.findById(rideId).orElseThrow(() -> new NotFoundException("Ride", rideId));
        BigDecimal paid = ride.getFareTotal() == null ? ride.getCancellationFee() : ride.getFareTotal().add(ride.getTip());
        if (Money.of(amount).compareTo(Money.of(paid)) > 0) {
            throw new InvalidRequestException("REFUND_TOO_HIGH", "A refund cannot be more than the " + Money.inr(paid) + " charged.");
        }
        wallet.credit(ride.getRiderId(), rideId, "REFUND", amount, "Refund on " + ride.getCode() + ": " + note);
        Instant now = clock.instant();
        Payment refund = new Payment();
        refund.setRideId(rideId);
        refund.setRiderId(ride.getRiderId());
        refund.setKind("REFUND");
        refund.setMethod(PaymentMethod.WALLET);
        refund.setAmount(Money.of(amount).negate());
        refund.setStatus(PaymentStatus.REFUNDED);
        refund.setReference(Codes.reference("RFD"));
        refund.setCreatedAt(now);
        refund.setUpdatedAt(now);
        payments.save(refund);
        rideLog.event(rideId, "REFUND", "ADMIN", "%s refunded to the rider's wallet by %s: %s"
                .formatted(Money.inr(Money.of(amount)), admin.name(), note));
        audit.record(admin, "RIDE_REFUND", "Ride " + ride.getCode(), Money.inr(Money.of(amount)) + ": " + note);
    }

    @Transactional
    public void resolveSos(AuthenticatedUser admin, long sosId, String note) {
        SosAlert alert = sosAlerts.findById(sosId).orElseThrow(() -> new NotFoundException("SOS", sosId));
        alert.setStatus("RESOLVED");
        alert.setResolvedAt(clock.instant());
        alert.setResolvedBy(admin.name());
        if (note != null && !note.isBlank()) {
            alert.setNote((alert.getNote() == null ? "" : alert.getNote() + " · ") + "Resolution: " + note);
        }
        sosAlerts.save(alert);
        rideLog.event(alert.getRideId(), "SOS_RESOLVED", "ADMIN", "SOS resolved by " + admin.name());
        audit.record(admin, "SOS_RESOLVED", "SOS #" + sosId, note == null ? "" : note);
    }

    // ------------------------------------------------------------------ pricing and settings

    @Transactional
    public VehicleType updateVehicleType(AuthenticatedUser admin, String code, AdminDtos.VehicleTypeUpdate u) {
        VehicleType vt = vehicleTypes.findById(code).orElseThrow(() -> new NotFoundException("Vehicle type", code));
        String before = fareCard(vt);
        vt.setName(u.name().trim());
        vt.setDescription(u.description().trim());
        vt.setCapacity(u.capacity());
        vt.setBaseFare(Money.of(u.baseFare()));
        vt.setBaseKm(u.baseKm());
        vt.setPerKm(Money.of(u.perKm()));
        vt.setPerMin(Money.of(u.perMin()));
        vt.setMinFare(Money.of(u.minFare()));
        vt.setWaitFreeMin(u.waitFreeMin());
        vt.setWaitPerMin(Money.of(u.waitPerMin()));
        vt.setCancelFee(Money.of(u.cancelFee()));
        vt.setActive(u.active());
        vt.setUpdatedAt(clock.instant());
        VehicleType saved = vehicleTypes.save(vt);
        audit.record(admin, "FARE_CARD_UPDATED", vt.getName(), before + " → " + fareCard(saved));
        return saved;
    }

    @Transactional
    public SurgeZone saveZone(AuthenticatedUser admin, Long id, AdminDtos.SurgeZoneRequest r) {
        SurgeZone zone = id == null ? new SurgeZone() : zones.findById(id).orElseThrow(() -> new NotFoundException("Surge zone", id));
        zone.setName(r.name().trim());
        zone.setLat(r.lat());
        zone.setLng(r.lng());
        zone.setRadiusM(r.radiusM());
        zone.setMode(r.mode());
        zone.setManualMultiplier(r.manualMultiplier().setScale(2, java.math.RoundingMode.HALF_UP));
        zone.setActive(r.active());
        zone.setUpdatedAt(clock.instant());
        SurgeZone saved = zones.save(zone);
        audit.record(admin, id == null ? "SURGE_ZONE_ADDED" : "SURGE_ZONE_UPDATED", saved.getName(),
                "%s %s, radius %d m%s".formatted(saved.getMode(), "MANUAL".equals(saved.getMode())
                                ? saved.getManualMultiplier() + "x" : "(demand-based)", saved.getRadiusM(),
                        saved.isActive() ? "" : ", inactive"));
        return saved;
    }

    @Transactional
    public void deleteZone(AuthenticatedUser admin, long id) {
        SurgeZone zone = zones.findById(id).orElseThrow(() -> new NotFoundException("Surge zone", id));
        zones.delete(zone);
        audit.record(admin, "SURGE_ZONE_DELETED", zone.getName(), "Removed");
    }

    @Transactional
    public PromoCode savePromo(AuthenticatedUser admin, AdminDtos.PromoRequest r) {
        if (r.percentOff() == null && r.flatOff() == null) {
            throw new InvalidRequestException("PROMO_KIND", "Set a percentage or a flat amount.");
        }
        String code = r.code().trim().toUpperCase(Locale.ROOT);
        PromoCode promo = promos.findById(code).orElseGet(() -> {
            PromoCode p = new PromoCode();
            p.setCode(code);
            p.setCreatedAt(clock.instant());
            return p;
        });
        boolean created = promo.getDescription() == null;
        promo.setDescription(r.description().trim());
        promo.setPercentOff(r.percentOff());
        promo.setFlatOff(r.percentOff() != null ? null : Money.of(r.flatOff()));
        promo.setMaxDiscount(r.percentOff() != null && r.maxDiscount() != null ? Money.of(r.maxDiscount()) : null);
        promo.setMinFare(Money.of(r.minFare()));
        promo.setUsesPerRider(r.usesPerRider());
        promo.setValidUntil(r.validUntil());
        promo.setActive(r.active());
        PromoCode saved = promos.save(promo);
        audit.record(admin, created ? "PROMO_CREATED" : "PROMO_UPDATED", code, saved.getDescription()
                + (saved.isActive() ? "" : " (inactive)"));
        return saved;
    }

    @Transactional
    public String updateSetting(AuthenticatedUser admin, Setting setting, String value) {
        String before = settings.all().get(setting);
        String after = settings.update(setting, value);
        audit.record(admin, "SETTING_CHANGED", setting.label(), before + " → " + after + " " + setting.unit());
        return after;
    }

    private Driver driver(long id) {
        return drivers.findById(id).orElseThrow(() -> new NotFoundException("Driver", id));
    }

    private Rider rider(long id) {
        return riders.findById(id).orElseThrow(() -> new NotFoundException("Rider", id));
    }

    private static String driverTarget(Driver d) {
        return "Driver " + d.getName() + " (" + d.getVehiclePlate() + ")";
    }

    private static String riderTarget(Rider r) {
        return "Rider " + r.getName() + " (" + r.getPhone() + ")";
    }

    private static String fareCard(VehicleType vt) {
        return "%s for %s km + %s/km + %s/min, min %s".formatted(Money.inr(vt.getBaseFare()),
                vt.getBaseKm().stripTrailingZeros().toPlainString(), Money.inr(vt.getPerKm()),
                Money.inr(vt.getPerMin()), Money.inr(vt.getMinFare()));
    }
}
