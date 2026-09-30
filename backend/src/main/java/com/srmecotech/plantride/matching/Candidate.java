package com.srmecotech.plantride.matching;

import com.srmecotech.plantride.masterdata.Driver;
import com.srmecotech.plantride.masterdata.Vehicle;
import com.srmecotech.plantride.trip.Duty;
import com.srmecotech.plantride.trip.Trip;

/**
 * A vehicle that passed every hard rule, with its score (lower is better).
 *
 * @param trip       the open trip the rider would join, or null for a fresh trip
 * @param seatsTaken peak seats already taken on the rider's stretch of the route
 * @param capacity   the effective cap: min(route occupancy cap, vehicle seats), or the
 *                   vehicle's seats for an exclusive ride
 * @param reason     human-readable explanation, stored on the booking for the desk
 */
public record Candidate(
        Vehicle vehicle,
        Driver driver,
        Duty duty,
        Trip trip,
        int etaMinutes,
        int seatsTaken,
        int capacity,
        double emptyKm,
        int detourMinutes,
        double score,
        String reason) {

    public int seatsFree() {
        return capacity - seatsTaken;
    }
}
