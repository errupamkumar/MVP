package com.srmecotech.plantride.matching;

/**
 * Published when something frees up capacity or adds demand (a driver signs
 * on, a trip closes, a booking is approved, a cab breaks down). Waiting
 * riders are matched right after the publishing transaction commits,
 * instead of waiting for the next 15-second sweep.
 */
public record DispatchRequestedEvent(String reason) {
}
