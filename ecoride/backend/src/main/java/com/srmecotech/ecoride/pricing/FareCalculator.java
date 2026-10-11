package com.srmecotech.ecoride.pricing;

import com.srmecotech.ecoride.common.util.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure fare arithmetic, shared by quotes, final settlement, the admin fare preview and tests.
 *
 * <pre>
 * ride     = base fare (covers base km) + per-km beyond base km + per-minute, at least the minimum fare
 * surge    = ride x (multiplier - 1)
 * waiting  = per-minute after the free wait at the pickup
 * gross    = ride + surge + waiting          (the driver's share is taken from this)
 * discount = promo, never more than gross
 * total    = (gross - discount) + GST, rounded to whole rupees
 * </pre>
 */
public final class FareCalculator {

    private FareCalculator() {
    }

    public static FareBreakdown calculate(VehicleType vt, double distanceM, double durationS, BigDecimal surgeMultiplier,
                                          int waitingS, Promo promo, double gstPct) {
        double distanceKm = round2(distanceM / 1000d);
        int durationMin = (int) Math.ceil(Math.max(0, durationS) / 60d);

        BigDecimal base = Money.of(vt.getBaseFare());
        BigDecimal extraKm = BigDecimal.valueOf(Math.max(0, distanceKm - vt.getBaseKm().doubleValue()));
        BigDecimal distanceFare = Money.of(extraKm.multiply(vt.getPerKm()));
        BigDecimal timeFare = Money.of(vt.getPerMin().multiply(BigDecimal.valueOf(durationMin)));
        BigDecimal ride = base.add(distanceFare).add(timeFare);
        BigDecimal topUp = Money.ZERO;
        if (ride.compareTo(vt.getMinFare()) < 0) {
            topUp = Money.of(vt.getMinFare().subtract(ride));
            ride = Money.of(vt.getMinFare());
        }

        BigDecimal multiplier = surgeMultiplier == null ? BigDecimal.ONE : surgeMultiplier.setScale(2, RoundingMode.HALF_UP);
        BigDecimal surgeFare = Money.of(ride.multiply(multiplier.subtract(BigDecimal.ONE)).max(BigDecimal.ZERO));

        int waitingMin = Math.max(0, (int) Math.ceil(waitingS / 60d) - vt.getWaitFreeMin());
        if (waitingS <= vt.getWaitFreeMin() * 60) {
            waitingMin = 0;
        }
        BigDecimal waitingFare = Money.of(vt.getWaitPerMin().multiply(BigDecimal.valueOf(waitingMin)));

        BigDecimal gross = ride.add(surgeFare).add(waitingFare);
        BigDecimal discount = promo == null ? Money.ZERO : Money.min(promo.discountOn(gross), gross);
        BigDecimal taxable = gross.subtract(discount);
        BigDecimal tax = Money.percent(taxable, gstPct);
        BigDecimal exact = taxable.add(tax);
        BigDecimal total = Money.wholeRupees(exact);

        return new FareBreakdown(
                vt.getCode(),
                distanceKm,
                durationMin,
                base,
                vt.getBaseKm(),
                distanceFare,
                timeFare,
                topUp,
                multiplier,
                surgeFare,
                waitingMin,
                waitingFare,
                gross,
                promo == null ? null : promo.code(),
                discount,
                BigDecimal.valueOf(gstPct).setScale(2, RoundingMode.HALF_UP),
                tax,
                Money.of(total.subtract(exact)),
                total);
    }

    private static double round2(double v) {
        return Math.round(v * 100d) / 100d;
    }

    /** A validated promo: a percentage (with an optional cap) or a flat amount. */
    public record Promo(String code, Integer percentOff, BigDecimal flatOff, BigDecimal maxDiscount) {

        public BigDecimal discountOn(BigDecimal gross) {
            BigDecimal discount;
            if (percentOff != null) {
                discount = Money.percent(gross, percentOff);
                if (maxDiscount != null) {
                    discount = Money.min(discount, maxDiscount);
                }
            } else {
                discount = Money.of(flatOff == null ? BigDecimal.ZERO : flatOff);
            }
            return discount.max(BigDecimal.ZERO);
        }
    }
}
