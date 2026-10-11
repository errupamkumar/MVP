package com.srmecotech.ecoride.pricing;

import java.math.BigDecimal;

/**
 * Every line of a fare, as shown on the rider's receipt. The lines add up:
 * baseFare + distanceFare + timeFare + minimumFareTopUp + surgeFare + waitingFare
 * - discount + tax + rounding = total.
 *
 * @param distanceKm       kilometres charged (planned route for an upfront fare, GPS for a recalculated one)
 * @param durationMin      minutes charged
 * @param baseKm           kilometres included in the base fare
 * @param minimumFareTopUp what was added to reach the minimum fare
 * @param waitingMin       chargeable waiting minutes (after the free wait)
 * @param fareBeforeDiscount everything the driver earns on: ride + surge + waiting
 */
public record FareBreakdown(
        String vehicleType,
        double distanceKm,
        int durationMin,
        BigDecimal baseFare,
        BigDecimal baseKm,
        BigDecimal distanceFare,
        BigDecimal timeFare,
        BigDecimal minimumFareTopUp,
        BigDecimal surgeMultiplier,
        BigDecimal surgeFare,
        int waitingMin,
        BigDecimal waitingFare,
        BigDecimal fareBeforeDiscount,
        String promoCode,
        BigDecimal discount,
        BigDecimal taxPct,
        BigDecimal tax,
        BigDecimal rounding,
        BigDecimal total) {
}
