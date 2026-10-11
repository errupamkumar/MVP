package com.srmecotech.ecoride.ride;

import com.srmecotech.ecoride.common.error.BusinessRuleException;
import com.srmecotech.ecoride.common.error.ForbiddenOperationException;
import com.srmecotech.ecoride.common.error.InvalidRequestException;
import com.srmecotech.ecoride.common.error.NotFoundException;
import com.srmecotech.ecoride.common.settings.Setting;
import com.srmecotech.ecoride.common.settings.SettingsService;
import com.srmecotech.ecoride.common.util.Money;
import com.srmecotech.ecoride.driver.Availability;
import com.srmecotech.ecoride.driver.Driver;
import com.srmecotech.ecoride.driver.DriverLedgerService;
import com.srmecotech.ecoride.driver.DriverRepository;
import com.srmecotech.ecoride.driver.DriverStatus;
import com.srmecotech.ecoride.geo.GeoMath;
import com.srmecotech.ecoride.geo.GeoPoint;
import com.srmecotech.ecoride.geo.RouteResult;
import com.srmecotech.ecoride.pricing.VehicleType;
import com.srmecotech.ecoride.pricing.VehicleTypeRepository;
import com.srmecotech.ecoride.rider.RiderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Every state change a driver (or an admin) makes to a ride. Each method locks the ride row,
 * then touches the driver row: the same order as GPS pings, so they never deadlock.
 */
@Service
public class RideLifecycleService {

    /** An accept that arrives this long after the countdown ended still counts (network lag). */
    private static final long ACCEPT_GRACE_S = 2;

    private final RideRepository rides;
    private final RideOfferRepository offers;
    private final DriverRepository drivers;
    private final RiderRepository riders;
    private final VehicleTypeRepository vehicleTypes;
    private final SettlementService settlement;
    private final DriverLedgerService ledger;
    private final PaymentRepository payments;
    private final SettingsService settings;
    private final DispatchScheduler dispatcher;
    private final RideLog rideLog;
    private final Clock clock;

    public RideLifecycleService(RideRepository rides, RideOfferRepository offers, DriverRepository drivers,
                                RiderRepository riders, VehicleTypeRepository vehicleTypes, SettlementService settlement,
                                DriverLedgerService ledger, PaymentRepository payments, SettingsService settings,
                                DispatchScheduler dispatcher, RideLog rideLog, Clock clock) {
        this.rides = rides;
        this.offers = offers;
        this.drivers = drivers;
        this.riders = riders;
        this.vehicleTypes = vehicleTypes;
        this.settlement = settlement;
        this.ledger = ledger;
        this.payments = payments;
        this.settings = settings;
        this.dispatcher = dispatcher;
        this.rideLog = rideLog;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ availability

    @Transactional
    public void goOnline(long driverId, GeoPoint at) {
        Driver driver = drivers.findById(driverId).orElseThrow(() -> new NotFoundException("Driver", driverId));
        if (driver.getStatus() == DriverStatus.PENDING_APPROVAL) {
            throw new BusinessRuleException("DRIVER_PENDING_APPROVAL",
                    "Your documents are being verified. You can go online once an EcoRide admin approves your account.");
        }
        if (driver.getStatus() == DriverStatus.SUSPENDED) {
            throw new BusinessRuleException("DRIVER_SUSPENDED", "Your account is suspended"
                    + (driver.getStatusReason() == null ? "." : ": " + driver.getStatusReason())
                    + " Contact EcoRide driver support.");
        }
        if (driver.getStatus() != DriverStatus.APPROVED) {
            throw new BusinessRuleException("DRIVER_NOT_APPROVED", "Your application was not approved.");
        }
        Instant now = clock.instant();
        if (at != null) {
            drivers.updateLocation(driverId, at.lat(), at.lng(), null, 0.0, now);
        }
        if (driver.getAvailability() == Availability.OFFLINE) {
            drivers.setAvailability(driverId, Availability.ONLINE, now);
        }
    }

    @Transactional
    public void goOffline(long driverId) {
        Driver driver = drivers.findById(driverId).orElseThrow(() -> new NotFoundException("Driver", driverId));
        if (driver.getAvailability() == Availability.ON_TRIP) {
            throw new BusinessRuleException("RIDE_IN_PROGRESS", "Finish or cancel your current ride before going offline.");
        }
        drivers.setAvailability(driverId, Availability.OFFLINE, null);
        if (offers.cancelPendingForDriver(driverId, null, clock.instant()) > 0) {
            dispatcher.kick();
        }
    }

    // ------------------------------------------------------------------ offers

    /**
     * @param pickupRoute road route from the driver's position to the pickup, computed by the
     *                    caller before this transaction so no lock is held during the HTTP call
     */
    @Transactional
    public Ride accept(long driverId, long offerId, RouteResult pickupRoute) {
        RideOffer probe = offers.findById(offerId).orElseThrow(() -> new NotFoundException("Offer", offerId));
        if (!probe.getDriverId().equals(driverId)) {
            throw new ForbiddenOperationException("This offer was sent to another driver.");
        }
        Ride ride = rides.lockById(probe.getRideId()).orElseThrow();
        RideOffer offer = offers.findById(offerId).orElseThrow();
        Instant now = clock.instant();
        if (offer.getStatus() != RideOffer.OfferStatus.PENDING || ride.getStatus() != RideStatus.SEARCHING) {
            throw new BusinessRuleException("OFFER_GONE", ride.getStatus() == RideStatus.CANCELLED
                    ? "The rider cancelled this request." : "This ride is no longer available.");
        }
        if (now.isAfter(offer.getExpiresAt().plusSeconds(ACCEPT_GRACE_S))) {
            throw new BusinessRuleException("OFFER_EXPIRED", "Too late: this request moved to another driver.");
        }
        if (drivers.transition(driverId, Availability.ONLINE, Availability.ON_TRIP) == 0) {
            throw new BusinessRuleException("DRIVER_NOT_AVAILABLE", "Go online to accept rides.");
        }
        Driver driver = drivers.findById(driverId).orElseThrow();
        offer.setStatus(RideOffer.OfferStatus.ACCEPTED);
        offer.setRespondedAt(now);
        offers.save(offer);
        offers.cancelPendingForDriver(driverId, offerId, now);
        drivers.countAccept(driverId);

        ride.setStatus(RideStatus.ACCEPTED);
        ride.setDriverId(driverId);
        ride.setAcceptedAt(now);
        if (pickupRoute != null) {
            ride.setPickupPolyline(pickupRoute.polyline());
            ride.setPickupDistanceM(pickupRoute.distanceM());
            ride.setPickupDurationS(pickupRoute.durationS());
        }
        if (driver.getLat() != null) {
            ride.setLastTrackLat(driver.getLat());
            ride.setLastTrackLng(driver.getLng());
            ride.setLastTrackAt(now);
        }
        rides.save(ride);
        rideLog.event(ride.getId(), "ACCEPTED", "DRIVER", "Accepted by %s (%s, %s)%s".formatted(driver.getName(),
                driver.vehicleLabel(), driver.getVehiclePlate(), pickupRoute == null ? ""
                        : ", %.1f km from the pickup".formatted(pickupRoute.distanceM() / 1000d)));
        return ride;
    }

    @Transactional
    public void decline(long driverId, long offerId) {
        RideOffer probe = offers.findById(offerId).orElseThrow(() -> new NotFoundException("Offer", offerId));
        if (!probe.getDriverId().equals(driverId)) {
            throw new ForbiddenOperationException("This offer was sent to another driver.");
        }
        rides.lockById(probe.getRideId()).orElseThrow();
        RideOffer offer = offers.findById(offerId).orElseThrow();
        if (offer.getStatus() != RideOffer.OfferStatus.PENDING) {
            return;
        }
        offer.setStatus(RideOffer.OfferStatus.DECLINED);
        offer.setRespondedAt(clock.instant());
        offers.save(offer);
        rideLog.event(offer.getRideId(), "DECLINED", "DRIVER", "Declined by " + driverName(driverId));
        dispatcher.kick();
    }

    // ------------------------------------------------------------------ the trip

    @Transactional
    public Ride arrived(long driverId, long rideId) {
        Ride ride = lockOwned(driverId, rideId);
        if (ride.getStatus() != RideStatus.ACCEPTED) {
            throw wrongState(ride, "You can mark arrival only on the way to the pickup.");
        }
        Driver driver = drivers.findById(driverId).orElseThrow();
        int radius = settings.getInt(Setting.ARRIVAL_RADIUS_M);
        if (driver.getLat() != null) {
            double away = GeoMath.distanceM(driver.getLat(), driver.getLng(), ride.getPickupLat(), ride.getPickupLng());
            if (away > radius) {
                throw new BusinessRuleException("TOO_FAR_FROM_PICKUP", "You are %s from the pickup. Mark arrived within %d m of it."
                        .formatted(distanceLabel(away), radius));
            }
        }
        ride.setStatus(RideStatus.ARRIVED);
        ride.setArrivedAt(clock.instant());
        rides.save(ride);
        VehicleType vt = vehicleTypes.findById(ride.getVehicleType()).orElseThrow();
        rideLog.event(rideId, "ARRIVED", "DRIVER", "Driver arrived at the pickup. Free waiting: %d min".formatted(vt.getWaitFreeMin()));
        return ride;
    }

    @Transactional
    public Ride start(long driverId, long rideId, String otp) {
        Ride ride = lockOwned(driverId, rideId);
        if (ride.getStatus() == RideStatus.ACCEPTED) {
            throw new BusinessRuleException("NOT_ARRIVED", "Mark 'Arrived' at the pickup before starting the trip.");
        }
        if (ride.getStatus() != RideStatus.ARRIVED) {
            throw wrongState(ride, "This trip cannot be started now.");
        }
        if (otp == null || !ride.getOtp().equals(otp.trim())) {
            rideLog.event(rideId, "OTP_MISMATCH", "DRIVER", "Wrong start code entered");
            throw new InvalidRequestException("OTP_MISMATCH",
                    "That code does not match. Ask the rider for the 4-digit code shown in their app.");
        }
        Driver driver = drivers.findById(driverId).orElseThrow();
        Instant now = clock.instant();
        ride.setStatus(RideStatus.IN_PROGRESS);
        ride.setStartedAt(now);
        ride.setLastTrackLat(driver.getLat() != null ? driver.getLat() : ride.getPickupLat());
        ride.setLastTrackLng(driver.getLng() != null ? driver.getLng() : ride.getPickupLng());
        ride.setLastTrackAt(now);
        rides.save(ride);
        long waited = ride.getArrivedAt() == null ? 0 : Duration.between(ride.getArrivedAt(), now).toSeconds();
        rideLog.event(rideId, "STARTED", "DRIVER", "Code verified, trip started after %d min %d s at the pickup"
                .formatted(waited / 60, waited % 60));
        return ride;
    }

    @Transactional
    public Ride complete(long driverId, long rideId) {
        Ride ride = lockOwned(driverId, rideId);
        if (ride.getStatus() != RideStatus.IN_PROGRESS) {
            throw wrongState(ride, "Only a trip in progress can be completed.");
        }
        Driver driver = drivers.findById(driverId).orElseThrow();
        Instant now = clock.instant();
        ride.setStatus(RideStatus.COMPLETED);
        ride.setCompletedAt(now);
        if (driver.getLat() != null) {
            double fromDrop = GeoMath.distanceM(driver.getLat(), driver.getLng(), ride.getDropLat(), ride.getDropLng());
            if (fromDrop > 1000) {
                rideLog.event(rideId, "EARLY_DROP", "DRIVER", "Trip ended %s from the booked drop".formatted(distanceLabel(fromDrop)));
            }
        }
        rideLog.event(rideId, "COMPLETED", "DRIVER", "Trip completed: %.2f km by GPS in %d min".formatted(
                ride.getActualDistanceM() / 1000d, Math.max(1, Duration.between(ride.getStartedAt(), now).toMinutes())));
        settlement.settle(ride);
        rides.save(ride);
        drivers.transition(driverId, Availability.ON_TRIP, Availability.ONLINE);
        return ride;
    }

    /** The driver confirms the rider paid the fare in cash. */
    @Transactional
    public Ride cashCollected(long driverId, long rideId) {
        Ride ride = lockOwned(driverId, rideId);
        if (ride.getStatus() != RideStatus.COMPLETED || ride.getPaymentMethod() != PaymentMethod.CASH) {
            throw wrongState(ride, "There is no cash to collect on this ride.");
        }
        if (ride.getPaymentStatus() == PaymentStatus.PAID) {
            return ride;
        }
        Instant now = clock.instant();
        ride.setPaymentStatus(PaymentStatus.PAID);
        rides.save(ride);
        payments.findFirstByRideIdAndKindOrderByIdDesc(rideId, "RIDE").ifPresent(p -> {
            p.setStatus(PaymentStatus.PAID);
            p.setUpdatedAt(now);
            payments.save(p);
        });
        ledger.add(driverId, rideId, DriverLedgerService.CASH_COLLECTED, ride.getFareTotal().negate(),
                "Cash %s collected on %s (kept by you)".formatted(Money.inr(ride.getFareTotal()), ride.getCode()));
        rideLog.event(rideId, "PAYMENT", "DRIVER", "Cash %s collected by the driver".formatted(Money.inr(ride.getFareTotal())));
        return ride;
    }

    @Transactional
    public Ride rateRider(long driverId, long rideId, int stars) {
        Ride ride = lockOwned(driverId, rideId);
        if (ride.getStatus() != RideStatus.COMPLETED) {
            throw wrongState(ride, "You can rate the rider after the trip.");
        }
        if (ride.getRiderRating() != null) {
            return ride;
        }
        ride.setRiderRating(stars);
        rides.save(ride);
        riders.addRating(ride.getRiderId(), stars);
        rideLog.event(rideId, "RATED_RIDER", "DRIVER", "Driver rated the rider " + stars + "/5");
        return ride;
    }

    /**
     * Before the trip starts. After waiting NO_SHOW_WAIT_MIN at the pickup this is a rider
     * no-show (the rider pays the cancellation fee, the driver gets their share). Earlier it is
     * a driver cancellation: a penalty (10% of the fare, at most ₹100) and the ride goes back
     * to searching for another driver.
     */
    @Transactional
    public Ride driverCancel(long driverId, long rideId, String reason) {
        Ride ride = lockOwned(driverId, rideId);
        if (ride.getStatus() != RideStatus.ACCEPTED && ride.getStatus() != RideStatus.ARRIVED) {
            throw wrongState(ride, ride.getStatus() == RideStatus.IN_PROGRESS
                    ? "The rider is on board: complete the trip instead." : "This ride cannot be cancelled now.");
        }
        Instant now = clock.instant();
        if (isNoShow(ride, now)) {
            ride.setStatus(RideStatus.CANCELLED);
            ride.setCancelledAt(now);
            ride.setCancelledBy("DRIVER");
            ride.setCancelReason("Rider did not come to the pickup");
            chargeCancellationFee(ride);
            rides.save(ride);
            drivers.transition(driverId, Availability.ON_TRIP, Availability.ONLINE);
            rideLog.event(rideId, "NO_SHOW", "DRIVER", "Rider no-show after %d min waiting; cancellation fee %s"
                    .formatted(Duration.between(ride.getArrivedAt(), now).toMinutes(), Money.inr(ride.getCancellationFee())));
            return ride;
        }
        BigDecimal penalty = Money.min(
                Money.percent(ride.getEstFare(), settings.getInt(Setting.DRIVER_CANCEL_PENALTY_PCT)),
                BigDecimal.valueOf(settings.getInt(Setting.DRIVER_CANCEL_PENALTY_CAP)));
        String why = reason == null || reason.isBlank() ? "no reason given" : reason.trim();
        if (penalty.signum() > 0) {
            ledger.add(driverId, rideId, DriverLedgerService.PENALTY, penalty.negate(),
                    "Cancelled accepted ride %s (%s)".formatted(ride.getCode(), why));
        }
        ride.setStatus(RideStatus.SEARCHING);
        ride.setDriverId(null);
        ride.setAcceptedAt(null);
        ride.setArrivedAt(null);
        ride.setPickupPolyline(null);
        ride.setPickupDistanceM(null);
        ride.setPickupDurationS(null);
        ride.setPickupTravelM(0);
        ride.setLastTrackLat(null);
        ride.setLastTrackLng(null);
        ride.setLastTrackAt(null);
        ride.setSearchStartedAt(now);
        rides.save(ride);
        drivers.transition(driverId, Availability.ON_TRIP, Availability.ONLINE);
        rideLog.event(rideId, "DRIVER_CANCELLED", "DRIVER", "%s cancelled (%s), penalty %s. Finding another driver."
                .formatted(driverName(driverId), why, Money.inr(penalty)));
        dispatcher.kick();
        return ride;
    }

    @Transactional
    public Ride adminCancel(long rideId, String adminName, String reason) {
        Ride ride = rides.lockById(rideId).orElseThrow(() -> new NotFoundException("Ride", rideId));
        if (!ride.getStatus().isActive()) {
            throw wrongState(ride, "Only a ride that has not ended can be cancelled.");
        }
        Instant now = clock.instant();
        offers.cancelPendingForRide(rideId, now);
        if (ride.getDriverId() != null && ride.getStatus().isAssigned()) {
            drivers.transition(ride.getDriverId(), Availability.ON_TRIP, Availability.ONLINE);
        }
        ride.setStatus(RideStatus.CANCELLED);
        ride.setCancelledAt(now);
        ride.setCancelledBy("ADMIN");
        ride.setCancelReason(reason);
        rides.save(ride);
        rideLog.event(rideId, "CANCELLED", "ADMIN", "Cancelled by %s: %s".formatted(adminName, reason));
        return ride;
    }

    // ------------------------------------------------------------------ helpers

    public boolean isNoShow(Ride ride, Instant now) {
        return ride.getStatus() == RideStatus.ARRIVED && ride.getArrivedAt() != null
                && Duration.between(ride.getArrivedAt(), now).toSeconds() >= settings.getInt(Setting.NO_SHOW_WAIT_MIN) * 60L;
    }

    /** Charges the product's cancellation fee and credits the driver's share. */
    void chargeCancellationFee(Ride ride) {
        VehicleType vt = vehicleTypes.findById(ride.getVehicleType()).orElseThrow();
        BigDecimal fee = Money.of(vt.getCancelFee());
        ride.setCancellationFee(fee);
        if (fee.signum() <= 0) {
            return;
        }
        settlement.collect(ride, "CANCEL_FEE", fee);
        if (ride.getDriverId() != null) {
            BigDecimal share = Money.of(fee.subtract(Money.percent(fee, settings.getInt(Setting.COMMISSION_PCT))));
            ledger.add(ride.getDriverId(), ride.getId(), DriverLedgerService.CANCEL_FEE, share,
                    "Cancellation fee share, ride " + ride.getCode());
        }
    }

    private Ride lockOwned(long driverId, long rideId) {
        Ride ride = rides.lockById(rideId).orElseThrow(() -> new NotFoundException("Ride", rideId));
        if (ride.getDriverId() == null || ride.getDriverId() != driverId) {
            throw new ForbiddenOperationException("This ride is not assigned to you.");
        }
        return ride;
    }

    private BusinessRuleException wrongState(Ride ride, String message) {
        return new BusinessRuleException("WRONG_RIDE_STATE", message + " (ride is " + ride.getStatus() + ")");
    }

    private String driverName(long driverId) {
        return drivers.findById(driverId).map(Driver::getName).orElse("Driver #" + driverId);
    }

    static String distanceLabel(double metres) {
        return metres < 1000 ? Math.round(metres) + " m" : "%.1f km".formatted(metres / 1000d);
    }
}
