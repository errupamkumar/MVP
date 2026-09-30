package com.srmecotech.plantride.booking;

public enum RideType {
    /** Pooled pick-up run: joins other riders on the route, up to the occupancy cap. */
    SHARED,
    /** The whole cab for one person or a group; released by approval or grade rule. */
    EXCLUSIVE
}
