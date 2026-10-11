package com.srmecotech.ecoride.driver;

public enum Availability {
    OFFLINE,
    /** Online and free: can receive ride offers. */
    ONLINE,
    /** Has an accepted ride (on the way, waiting, or with the rider on board). */
    ON_TRIP
}
