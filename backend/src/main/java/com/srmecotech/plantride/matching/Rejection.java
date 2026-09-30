package com.srmecotech.plantride.matching;

/** A vehicle the engine dropped, and the hard rule that dropped it. */
public record Rejection(Long vehicleId, String vehicleCode, String reason) {

    public String describe() {
        return vehicleCode + " " + reason;
    }
}
