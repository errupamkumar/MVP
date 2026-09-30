package com.srmecotech.plantride.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {

    private Money() {
    }

    public static BigDecimal rupees(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal km(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
