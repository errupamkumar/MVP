package com.srmecotech.plantride.masterdata;

/**
 * Administrative status only. Operational state (on trip, idle, off duty)
 * is derived from duties and trips, so it can never drift out of sync.
 */
public enum VehicleStatus {
    ACTIVE,
    /** Breakdown, accident or workshop: cannot start a duty or be matched. */
    OFF_ROAD
}
