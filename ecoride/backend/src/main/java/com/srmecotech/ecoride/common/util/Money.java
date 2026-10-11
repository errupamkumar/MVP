package com.srmecotech.ecoride.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private Money() {
    }

    public static BigDecimal of(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal of(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    /** Fares are charged in whole rupees. */
    public static BigDecimal wholeRupees(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal percent(BigDecimal amount, double percent) {
        return of(amount.multiply(BigDecimal.valueOf(percent)).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
    }

    /** "₹174" or "₹42.50", for timelines, ledgers and audit messages. */
    public static String inr(BigDecimal value) {
        if (value == null) {
            return "₹0";
        }
        BigDecimal v = value.setScale(2, RoundingMode.HALF_UP);
        String sign = v.signum() < 0 ? "-" : "";
        v = v.abs();
        return sign + "₹" + (v.stripTrailingZeros().scale() <= 0 ? v.setScale(0, RoundingMode.HALF_UP).toPlainString() : v.toPlainString());
    }

    public static BigDecimal max(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) <= 0 ? a : b;
    }
}
