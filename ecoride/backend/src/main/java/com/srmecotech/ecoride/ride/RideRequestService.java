package com.srmecotech.ecoride.ride;

import com.srmecotech.ecoride.common.error.BusinessRuleException;
import com.srmecotech.ecoride.common.error.ForbiddenOperationException;
import com.srmecotech.ecoride.common.error.InvalidRequestException;
import com.srmecotech.ecoride.common.error.NotFoundException;
import com.srmecotech.ecoride.common.settings.Setting;
import com.srmecotech.ecoride.common.settings.SettingsService;
import com.srmecotech.ecoride.common.util.Codes;
import com.srmecotech.ecoride.common.util.Money;
import com.srmecotech.ecoride.driver.Availability;
import com.srmecotech.ecoride.driver.DriverLedgerService;
import com.srmecotech.ecoride.driver.DriverRepository;
import com.srmecotech.ecoride.geo.GeoPoint;
import com.srmecotech.ecoride.pricing.FareQuote;
import com.srmecotech.ecoride.pricing.QuoteService;
import com.srmecotech.ecoride.rider.Rider;
import com.srmecotech.ecoride.rider.RiderRepository;
import com.srmecotech.ecoride.rider.RiderStatus;
import com.srmecotech.ecoride.rider.WalletService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Everything the rider does to a ride: request, cancel, share location, rate and tip, SOS. */
@Service
public class RideRequestService {

    private final RideRepository rides;
    private final RideOfferRepository offers;
    private final RiderRepository riders;
    private final DriverRepository drivers;
    private final QuoteService quotes;
    private final WalletService wallet;
    private final SettlementService settlement;
    private final RideLifecycleService lifecycle;
    private final DriverLedgerService ledger;
    private final SosAlertRepository sosAlerts;
    private final SettingsService settings;
    private final DispatchScheduler dispatcher;
    private final RideLog rideLog;
    private final Clock clock;

    public RideRequestService(RideRepository rides, RideOfferRepository offers, RiderRepository riders,
                              DriverRepository drivers, QuoteService quotes, WalletService wallet,
                              SettlementService settlement, RideLifecycleService lifecycle, DriverLedgerService ledger,
                              SosAlertRepository sosAlerts, SettingsService settings, DispatchScheduler dispatcher,
                              RideLog rideLog, Clock clock) {
        this.rides = rides;
        this.offers = offers;
        this.riders = riders;
        this.drivers = drivers;
        this.quotes = quotes;
        this.wallet = wallet;
        this.settlement = settlement;
        this.lifecycle = lifecycle;
        this.ledger = ledger;
        this.sosAlerts = sosAlerts;
        this.settings = settings;
        this.dispatcher = dispatcher;
        this.rideLog = rideLog;
        this.clock = clock;
    }

    @Transactional
    public Ride request(long riderId, String quoteId, String vehicleType, PaymentMethod method) {
        Rider rider = riders.findById(riderId).orElseThrow(() -> new NotFoundException("Rider", riderId));
        if (rider.getStatus() != RiderStatus.ACTIVE) {
            throw new BusinessRuleException("RIDER_BLOCKED", "Your account cannot book rides. Contact EcoRide support.");
        }
        rides.findFirstByRiderIdAndStatusInOrderByIdDesc(riderId, RideStatus.ACTIVE).ifPresent(r -> {
            throw new BusinessRuleException("RIDE_IN_PROGRESS", "You already have a ride in progress (" + r.getCode() + ").");
        });
        FareQuote quote = quotes.held(quoteId, riderId);
        FareQuote.QuoteOption option = quote.getOptions().items().stream()
                .filter(o -> o.vehicleType().equals(vehicleType))
                .findFirst()
                .orElseThrow(() -> new InvalidRequestException("VEHICLE_TYPE_UNKNOWN", "Choose one of the rides shown."));
        if (method == PaymentMethod.WALLET && wallet.balance(riderId).compareTo(option.fare().total()) < 0) {
            throw new BusinessRuleException("WALLET_LOW", "Your wallet has %s, less than the %s fare. Add money or pay another way."
                    .formatted(Money.inr(wallet.balance(riderId)), Money.inr(option.fare().total())));
        }
        Instant now = clock.instant();
        Ride ride = new Ride();
        ride.setCode(Codes.rideCode());
        ride.setRiderId(riderId);
        ride.setVehicleType(vehicleType);
        ride.setStatus(RideStatus.SEARCHING);
        ride.setPickupLat(quote.getPickupLat());
        ride.setPickupLng(quote.getPickupLng());
        ride.setPickupAddress(quote.getPickupAddress());
        ride.setDropLat(quote.getDropLat());
        ride.setDropLng(quote.getDropLng());
        ride.setDropAddress(quote.getDropAddress());
        ride.setEstDistanceM(quote.getDistanceM());
        ride.setEstDurationS(quote.getDurationS());
        ride.setRoutePolyline(quote.getPolyline());
        ride.setRouteProvider(quote.getRouteProvider());
        ride.setSurgeMultiplier(quote.getSurgeMultiplier());
        ride.setPromoCode(option.fare().promoCode());
        ride.setQuote(option.fare());
        ride.setEstFare(option.fare().total());
        ride.setPaymentMethod(method);
        ride.setPaymentStatus(PaymentStatus.PENDING);
        ride.setOtp(Codes.otp());
        ride.setRequestedAt(now);
        ride.setSearchStartedAt(now);
        ride.setCancellationFee(Money.ZERO);
        ride.setTip(Money.ZERO);
        ride.setSimulated(rider.isSimulated());
        rides.saveAndFlush(ride);
        rideLog.event(ride.getId(), "REQUESTED", "RIDER", "%s requested %s: %.1f km, upfront %s%s, pay by %s".formatted(
                rider.getName(), option.name(), quote.getDistanceM() / 1000d, Money.inr(option.fare().total()),
                quote.getSurgeMultiplier().compareTo(BigDecimal.ONE) > 0 ? " (surge " + quote.getSurgeMultiplier() + "x)" : "",
                method));
        dispatcher.kick();
        return ride;
    }

    @Transactional
    public Ride cancel(long riderId, long rideId, String reason) {
        Ride ride = rides.lockById(rideId).orElseThrow(() -> new NotFoundException("Ride", rideId));
        requireOwner(ride, riderId);
        if (ride.getStatus() == RideStatus.IN_PROGRESS) {
            throw new BusinessRuleException("TRIP_IN_PROGRESS", "Your trip has started. Ask the driver to end it at your stop.");
        }
        if (!ride.getStatus().isActive()) {
            return ride;
        }
        Instant now = clock.instant();
        boolean feeApplies = cancellationFeeApplies(ride, now);
        Long driverId = ride.getStatus().isAssigned() ? ride.getDriverId() : null;
        offers.cancelPendingForRide(rideId, now);
        ride.setStatus(RideStatus.CANCELLED);
        ride.setCancelledAt(now);
        ride.setCancelledBy("RIDER");
        ride.setCancelReason(reason == null || reason.isBlank() ? "Changed my plans" : reason.trim());
        if (feeApplies) {
            lifecycle.chargeCancellationFee(ride);
        }
        rides.save(ride);
        if (driverId != null) {
            drivers.transition(driverId, Availability.ON_TRIP, Availability.ONLINE);
        }
        rideLog.event(rideId, "CANCELLED", "RIDER", "Rider cancelled (%s)%s".formatted(ride.getCancelReason(),
                feeApplies ? ", cancellation fee " + Money.inr(ride.getCancellationFee()) : ", no charge"));
        return ride;
    }

    /** Free while searching and for RIDER_CANCEL_GRACE_S after a driver accepts; charged once the driver has arrived. */
    public boolean cancellationFeeApplies(Ride ride, Instant now) {
        return switch (ride.getStatus()) {
            case ACCEPTED -> ride.getAcceptedAt() != null
                    && Duration.between(ride.getAcceptedAt(), now).toSeconds() > settings.getInt(Setting.RIDER_CANCEL_GRACE_S);
            case ARRIVED -> true;
            default -> false;
        };
    }

    /** Seconds of free cancellation left, or null when it is free regardless (searching) or never free (arrived). */
    public Integer freeCancelLeftS(Ride ride, Instant now) {
        if (ride.getStatus() != RideStatus.ACCEPTED || ride.getAcceptedAt() == null) {
            return null;
        }
        long left = settings.getInt(Setting.RIDER_CANCEL_GRACE_S) - Duration.between(ride.getAcceptedAt(), now).toSeconds();
        return (int) Math.max(0, left);
    }

    /** The rider's own position, shown to the driver while the cab is on its way. */
    @Transactional
    public void shareLocation(long riderId, long rideId, GeoPoint at) {
        Ride ride = rides.findById(rideId).orElseThrow(() -> new NotFoundException("Ride", rideId));
        requireOwner(ride, riderId);
        if (ride.getStatus() != RideStatus.ACCEPTED && ride.getStatus() != RideStatus.ARRIVED) {
            return;
        }
        rides.updateRiderLocation(rideId, at.lat(), at.lng(), clock.instant());
    }

    @Transactional
    public Ride rate(long riderId, long rideId, int stars, List<String> tags, String comment, BigDecimal tip) {
        Ride ride = rides.lockById(rideId).orElseThrow(() -> new NotFoundException("Ride", rideId));
        requireOwner(ride, riderId);
        if (ride.getStatus() != RideStatus.COMPLETED) {
            throw new BusinessRuleException("WRONG_RIDE_STATE", "You can rate the driver after the trip.");
        }
        if (ride.getDriverRating() != null) {
            throw new BusinessRuleException("ALREADY_RATED", "You have already rated this trip.");
        }
        StringBuilder feedback = new StringBuilder();
        if (tags != null && !tags.isEmpty()) {
            feedback.append(String.join(", ", tags));
        }
        if (comment != null && !comment.isBlank()) {
            if (!feedback.isEmpty()) {
                feedback.append(" · ");
            }
            feedback.append(comment.trim());
        }
        ride.setDriverRating(stars);
        ride.setDriverFeedback(feedback.isEmpty() ? null : feedback.length() > 255 ? feedback.substring(0, 255) : feedback.toString());
        drivers.addRating(ride.getDriverId(), stars);
        BigDecimal tipAmount = tip == null ? Money.ZERO : Money.of(tip);
        if (tipAmount.signum() > 0) {
            ride.setTip(tipAmount);
            settlement.collect(ride, "TIP", tipAmount);
            ledger.add(ride.getDriverId(), rideId, DriverLedgerService.TIP, tipAmount, "Tip on ride " + ride.getCode());
        }
        rides.save(ride);
        rideLog.event(rideId, "RATED_DRIVER", "RIDER", "Rider rated the driver %d/5%s".formatted(stars,
                tipAmount.signum() > 0 ? " and tipped " + Money.inr(tipAmount) : ""));
        return ride;
    }

    @Transactional
    public SosAlert sos(long rideId, String raisedBy, GeoPoint at, String note) {
        Ride ride = rides.findById(rideId).orElseThrow(() -> new NotFoundException("Ride", rideId));
        SosAlert alert = new SosAlert();
        alert.setRideId(ride.getId());
        alert.setRaisedBy(raisedBy);
        alert.setLat(at == null ? null : at.lat());
        alert.setLng(at == null ? null : at.lng());
        alert.setStatus("OPEN");
        alert.setNote(note);
        alert.setCreatedAt(clock.instant());
        rideLog.event(rideId, "SOS", raisedBy, "SOS raised by the " + raisedBy.toLowerCase() + (note == null ? "" : ": " + note));
        return sosAlerts.save(alert);
    }

    public void requireOwner(Ride ride, long riderId) {
        if (ride.getRiderId() != riderId) {
            throw new ForbiddenOperationException("This ride belongs to another rider.");
        }
    }
}
