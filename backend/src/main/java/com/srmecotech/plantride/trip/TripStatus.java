package com.srmecotech.plantride.trip;

import java.util.EnumSet;
import java.util.Set;

public enum TripStatus {
    /** Riders assigned, cab heading to the first pickup. */
    PLANNED,
    /** At least one rider has boarded with their OTP. */
    IN_PROGRESS,
    /** Every rider dropped (or no-show); the trip has been costed. */
    COMPLETED,
    /** Closed with nobody carried: every booking cancelled, re-matched or a no-show. */
    CANCELLED;

    public static final Set<TripStatus> OPEN = EnumSet.of(PLANNED, IN_PROGRESS);
}
