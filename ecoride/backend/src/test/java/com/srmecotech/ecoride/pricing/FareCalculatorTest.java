package com.srmecotech.ecoride.pricing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class FareCalculatorTest {

    private static final double GST = 5;

    /** Mini: ₹75 for the first 3 km, ₹21/km after, ₹1.50/min, minimum ₹75, 3 min free wait then ₹2/min. */
    private static VehicleType mini() {
        VehicleType vt = new VehicleType();
        vt.setCode("MINI");
        vt.setBaseFare(new BigDecimal("75"));
        vt.setBaseKm(new BigDecimal("3"));
        vt.setPerKm(new BigDecimal("21"));
        vt.setPerMin(new BigDecimal("1.5"));
        vt.setMinFare(new BigDecimal("75"));
        vt.setWaitFreeMin(3);
        vt.setWaitPerMin(new BigDecimal("2"));
        vt.setCancelFee(new BigDecimal("50"));
        return vt;
    }

    @Test
    void baseFareCoversTheFirstThreeKm() {
        FareBreakdown f = FareCalculator.calculate(mini(), 2_500, 0, BigDecimal.ONE, 0, null, GST);
        assertThat(f.distanceFare()).isEqualByComparingTo("0");
        assertThat(f.baseFare()).isEqualByComparingTo("75");
        // 75 + 5% GST = 78.75 -> ₹79
        assertThat(f.tax()).isEqualByComparingTo("3.75");
        assertThat(f.total()).isEqualByComparingTo("79");
    }

    @Test
    void distanceBeyondBaseAndTimeAreCharged() {
        // 8.4 km, 17 min: 75 + 5.4 x 21 (113.40) + 17 x 1.5 (25.50) = 213.90, + 10.70 GST = 224.60 -> ₹225
        FareBreakdown f = FareCalculator.calculate(mini(), 8_400, 17 * 60, BigDecimal.ONE, 0, null, GST);
        assertThat(f.distanceFare()).isEqualByComparingTo("113.40");
        assertThat(f.timeFare()).isEqualByComparingTo("25.50");
        assertThat(f.fareBeforeDiscount()).isEqualByComparingTo("213.90");
        assertThat(f.total()).isEqualByComparingTo("225");
        assertLinesAddUp(f);
    }

    @Test
    void partMinutesRoundUp() {
        FareBreakdown f = FareCalculator.calculate(mini(), 1_000, 61, BigDecimal.ONE, 0, null, GST);
        assertThat(f.durationMin()).isEqualTo(2);
    }

    @Test
    void minimumFareTopsUpShortCheapRides() {
        VehicleType vt = mini();
        vt.setBaseFare(new BigDecimal("40"));
        vt.setMinFare(new BigDecimal("60"));
        FareBreakdown f = FareCalculator.calculate(vt, 1_000, 60, BigDecimal.ONE, 0, null, GST);
        // 40 + 1.5 = 41.50 -> topped up by 18.50 to 60
        assertThat(f.minimumFareTopUp()).isEqualByComparingTo("18.50");
        assertThat(f.fareBeforeDiscount()).isEqualByComparingTo("60");
        assertLinesAddUp(f);
    }

    @Test
    void surgeMultipliesTheRideFareOnly() {
        FareBreakdown f = FareCalculator.calculate(mini(), 8_400, 17 * 60, new BigDecimal("1.3"), 5 * 60, null, GST);
        // ride 213.90 x 0.3 = 64.17 surge; waiting (5 - 3 free) x 2 = 4 is not surged
        assertThat(f.surgeFare()).isEqualByComparingTo("64.17");
        assertThat(f.waitingFare()).isEqualByComparingTo("4");
        assertThat(f.fareBeforeDiscount()).isEqualByComparingTo("282.07");
        assertLinesAddUp(f);
    }

    @Test
    void freeWaitingIsFree() {
        assertThat(FareCalculator.calculate(mini(), 5_000, 600, BigDecimal.ONE, 180, null, GST).waitingFare()).isEqualByComparingTo("0");
        // 3 min 10 s: one chargeable minute
        assertThat(FareCalculator.calculate(mini(), 5_000, 600, BigDecimal.ONE, 190, null, GST).waitingMin()).isEqualTo(1);
    }

    @Test
    void percentagePromoIsCappedAndTaxIsOnTheDiscountedFare() {
        FareCalculator.Promo eco20 = new FareCalculator.Promo("ECO20", 20, null, new BigDecimal("60"));
        FareBreakdown f = FareCalculator.calculate(mini(), 20_000, 40 * 60, BigDecimal.ONE, 0, eco20, GST);
        // gross 75 + 17 x 21 + 40 x 1.5 = 492; 20% = 98.40 capped at 60
        assertThat(f.fareBeforeDiscount()).isEqualByComparingTo("492");
        assertThat(f.discount()).isEqualByComparingTo("60");
        assertThat(f.tax()).isEqualByComparingTo("21.60");
        assertThat(f.total()).isEqualByComparingTo("454");
        assertLinesAddUp(f);
    }

    @Test
    void flatPromoNeverMakesTheFareNegative() {
        FareCalculator.Promo big = new FareCalculator.Promo("HUGE", null, new BigDecimal("500"), null);
        FareBreakdown f = FareCalculator.calculate(mini(), 1_000, 60, BigDecimal.ONE, 0, big, GST);
        assertThat(f.discount()).isEqualByComparingTo(f.fareBeforeDiscount());
        assertThat(f.total()).isEqualByComparingTo("0");
    }

    private static void assertLinesAddUp(FareBreakdown f) {
        BigDecimal sum = f.baseFare().add(f.distanceFare()).add(f.timeFare()).add(f.minimumFareTopUp()).add(f.surgeFare())
                .add(f.waitingFare()).subtract(f.discount()).add(f.tax()).add(f.rounding());
        assertThat(sum).isEqualByComparingTo(f.total());
    }
}
