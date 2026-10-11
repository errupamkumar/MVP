package com.srmecotech.ecoride.ride;

import com.srmecotech.ecoride.common.settings.Setting;
import com.srmecotech.ecoride.common.settings.SettingsService;
import com.srmecotech.ecoride.common.util.Codes;
import com.srmecotech.ecoride.common.util.Money;
import com.srmecotech.ecoride.driver.DriverLedgerService;
import com.srmecotech.ecoride.pricing.FareBreakdown;
import com.srmecotech.ecoride.pricing.FareCalculator;
import com.srmecotech.ecoride.pricing.PromoCode;
import com.srmecotech.ecoride.pricing.PromoCodeRepository;
import com.srmecotech.ecoride.pricing.VehicleType;
import com.srmecotech.ecoride.pricing.VehicleTypeRepository;
import com.srmecotech.ecoride.rider.WalletService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Money at the end of a ride: the final fare, the payment, and the driver's share.
 * <p>
 * Fare policy (upfront pricing): the price the rider accepted stands, plus any waiting
 * charge, unless the GPS distance driven with the rider on board differs from the planned
 * route by more than FARE_RECALC_TOLERANCE_PCT (a detour or a changed drop). Then the fare
 * is recalculated from the GPS distance and the actual trip time.
 */
@Service
public class SettlementService {

    private final VehicleTypeRepository vehicleTypes;
    private final PromoCodeRepository promos;
    private final SettingsService settings;
    private final WalletService wallet;
    private final DriverLedgerService ledger;
    private final PaymentRepository payments;
    private final RideLog rideLog;
    private final Clock clock;

    public SettlementService(VehicleTypeRepository vehicleTypes, PromoCodeRepository promos, SettingsService settings,
                             WalletService wallet, DriverLedgerService ledger, PaymentRepository payments,
                             RideLog rideLog, Clock clock) {
        this.vehicleTypes = vehicleTypes;
        this.promos = promos;
        this.settings = settings;
        this.wallet = wallet;
        this.ledger = ledger;
        this.payments = payments;
        this.rideLog = rideLog;
        this.clock = clock;
    }

    /** Fills the ride's final fare and payment fields; the caller saves the ride. */
    public void settle(Ride ride) {
        VehicleType vt = vehicleTypes.findById(ride.getVehicleType()).orElseThrow();
        Instant completed = ride.getCompletedAt();
        int waitingS = ride.getArrivedAt() == null || ride.getStartedAt() == null ? 0
                : (int) Duration.between(ride.getArrivedAt(), ride.getStartedAt()).toSeconds();
        int tripS = (int) Duration.between(ride.getStartedAt(), completed).toSeconds();
        FareCalculator.Promo promo = ride.getPromoCode() == null ? null
                : promos.findById(ride.getPromoCode()).map(PromoCode::toPromo).orElse(null);
        double gst = settings.getDouble(Setting.GST_PCT);

        double deviationPct = ride.getEstDistanceM() == 0 ? 0
                : Math.abs(ride.getActualDistanceM() - ride.getEstDistanceM()) * 100d / ride.getEstDistanceM();
        boolean upfront = deviationPct <= settings.getInt(Setting.FARE_RECALC_TOLERANCE_PCT);
        FareBreakdown fare = upfront
                ? FareCalculator.calculate(vt, ride.getEstDistanceM(), ride.getEstDurationS(), ride.getSurgeMultiplier(),
                waitingS, promo, gst)
                : FareCalculator.calculate(vt, ride.getActualDistanceM(), tripS, ride.getSurgeMultiplier(), waitingS, promo, gst);

        BigDecimal commissionPct = BigDecimal.valueOf(settings.getInt(Setting.COMMISSION_PCT));
        BigDecimal commission = Money.percent(fare.fareBeforeDiscount(), commissionPct.doubleValue());
        BigDecimal earning = Money.of(fare.fareBeforeDiscount().subtract(commission));

        ride.setFare(fare);
        ride.setFareTotal(fare.total());
        ride.setFarePolicy(upfront ? "UPFRONT" : "RECALCULATED");
        ride.setTax(fare.tax());
        ride.setDiscount(fare.discount());
        ride.setCommission(commission);
        ride.setDriverEarning(earning);

        rideLog.event(ride.getId(), "FARE", "SYSTEM", upfront
                ? "GPS distance %.2f km vs planned %.2f km (%.0f%% apart): upfront fare %s stands%s".formatted(
                ride.getActualDistanceM() / 1000d, ride.getEstDistanceM() / 1000d, deviationPct, Money.inr(fare.total()),
                fare.waitingFare().signum() > 0 ? " plus " + Money.inr(fare.waitingFare()) + " waiting" : "")
                : "GPS distance %.2f km vs planned %.2f km (%.0f%% apart): fare recalculated to %s".formatted(
                ride.getActualDistanceM() / 1000d, ride.getEstDistanceM() / 1000d, deviationPct, Money.inr(fare.total())));

        collect(ride, "RIDE", fare.total());
        ledger.add(ride.getDriverId(), ride.getId(), DriverLedgerService.EARNING, earning,
                "Ride %s: fare %s, your share %s%%".formatted(ride.getCode(), Money.inr(fare.fareBeforeDiscount()),
                        BigDecimal.valueOf(100).subtract(commissionPct).toPlainString()));
    }

    /**
     * Takes a payment by the ride's method. Wallet falls back to cash when the balance is
     * short; UPI is a simulated collect that succeeds; cash stays pending until the driver
     * confirms it.
     */
    public Payment collect(Ride ride, String kind, BigDecimal amount) {
        Instant now = clock.instant();
        Payment payment = new Payment();
        payment.setRideId(ride.getId());
        payment.setRiderId(ride.getRiderId());
        payment.setKind(kind);
        payment.setAmount(Money.of(amount));
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);
        PaymentMethod method = ride.getPaymentMethod();
        if (method == PaymentMethod.WALLET) {
            boolean paid = wallet.tryDebit(ride.getRiderId(), ride.getId(), kind.equals("RIDE") ? "RIDE" : kind,
                    amount, label(kind) + " " + ride.getCode());
            if (paid) {
                payment.setMethod(PaymentMethod.WALLET);
                payment.setStatus(PaymentStatus.PAID);
                payment.setReference(Codes.reference("WAL"));
            } else if (kind.equals("RIDE")) {
                ride.setPaymentMethod(PaymentMethod.CASH);
                payment.setMethod(PaymentMethod.CASH);
                payment.setStatus(PaymentStatus.PENDING);
                rideLog.event(ride.getId(), "PAYMENT", "SYSTEM", "Wallet balance too low: the rider pays %s in cash"
                        .formatted(Money.inr(amount)));
            } else {
                payment.setMethod(PaymentMethod.UPI);
                payment.setStatus(PaymentStatus.PAID);
                payment.setReference(Codes.reference("UPI"));
            }
        } else if (method == PaymentMethod.UPI || !kind.equals("RIDE")) {
            payment.setMethod(PaymentMethod.UPI);
            payment.setStatus(PaymentStatus.PAID);
            payment.setReference(Codes.reference("UPI"));
        } else {
            payment.setMethod(PaymentMethod.CASH);
            payment.setStatus(PaymentStatus.PENDING);
        }
        if (kind.equals("RIDE")) {
            ride.setPaymentStatus(payment.getStatus());
        }
        Payment saved = payments.save(payment);
        if (saved.getStatus() == PaymentStatus.PAID) {
            rideLog.event(ride.getId(), "PAYMENT", "SYSTEM", "%s %s paid by %s".formatted(label(kind),
                    Money.inr(amount), saved.getMethod()));
        }
        return saved;
    }

    private static String label(String kind) {
        return switch (kind) {
            case "TIP" -> "Tip";
            case "CANCEL_FEE" -> "Cancellation fee";
            case "REFUND" -> "Refund";
            default -> "Fare";
        };
    }
}
