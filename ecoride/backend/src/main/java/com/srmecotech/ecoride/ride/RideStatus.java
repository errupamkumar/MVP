package com.srmecotech.ecoride.ride;

import java.util.EnumSet;
import java.util.Set;

/**
 * <pre>
 * SEARCHING --accept--> ACCEPTED --arrived--> ARRIVED --OTP--> IN_PROGRESS --complete--> COMPLETED
 *     |                    |  (driver cancels: back to SEARCHING)  |
 *     +--> NO_DRIVERS      +---------------> CANCELLED <-----------+
 * </pre>
 */
public enum RideStatus {
    SEARCHING,
    ACCEPTED,
    ARRIVED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    NO_DRIVERS;

    public static final Set<RideStatus> ACTIVE = EnumSet.of(SEARCHING, ACCEPTED, ARRIVED, IN_PROGRESS);
    public static final Set<RideStatus> ASSIGNED = EnumSet.of(ACCEPTED, ARRIVED, IN_PROGRESS);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }

    public boolean isAssigned() {
        return ASSIGNED.contains(this);
    }
}
