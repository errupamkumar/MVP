package com.srmecotech.plantride.masterdata;

import java.math.BigDecimal;

/** A pickup and drop resolved onto one configured route. */
public record ResolvedRide(RoutePlan plan, int fromSeq, int toSeq) {

    public Route route() {
        return plan.route();
    }

    public Stop from() {
        return plan.stopAt(fromSeq);
    }

    public Stop to() {
        return plan.stopAt(toSeq);
    }

    public int minutes() {
        return plan.minutesBetween(fromSeq, toSeq);
    }

    public BigDecimal km() {
        return plan.kmBetween(fromSeq, toSeq);
    }
}
