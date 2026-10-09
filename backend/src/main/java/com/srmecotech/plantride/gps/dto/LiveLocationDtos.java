package com.srmecotech.plantride.gps.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDateTime;

/** Live positions: the cab as its rider sees it, and a waiting rider as their driver sees them. */
public final class LiveLocationDtos {

    private LiveLocationDtos() {
    }

    /**
     * Where a cab is, from its driver's phone. When the phone has not reported
     * for a while (no signal, location off) this is the last fix or the last
     * stop the driver confirmed, and {@code live} is false.
     *
     * @param secondsAgo      since the phone last reported; null if it never has
     * @param live            the phone reported in the last two minutes
     * @param nearStopName    the nearest stop on the route within 300 m, if any
     * @param towardsStopName the stop {@code metresToStop} is measured to: the rider's pickup
     *                        (or drop once on board), or the driver's next stop
     * @param metresToStop    straight-line distance to that stop
     */
    public record CabLocationDto(
            String vehicleCode,
            double latitude,
            double longitude,
            Integer accuracyM,
            Double speedKmh,
            Integer heading,
            LocalDateTime updatedAt,
            Long secondsAgo,
            boolean live,
            String nearStopName,
            String towardsStopName,
            Integer metresToStop) {
    }

    /**
     * A waiting rider's position. Shared only while their cab is on its way,
     * seen only by that cab's driver (and the rider), and deleted at boarding.
     *
     * @param live          the rider's phone reported in the last two minutes
     * @param atPickup      within the pickup point's arrival radius
     * @param metresFromCab straight-line distance from the cab, when the cab's position is known
     */
    public record RiderLocationDto(
            double latitude,
            double longitude,
            Integer accuracyM,
            LocalDateTime updatedAt,
            long secondsAgo,
            boolean live,
            String pickupName,
            int metresToPickup,
            boolean atPickup,
            Integer metresFromCab) {
    }

    /**
     * @param ageSeconds how old the fix already is on the phone (sent instead of a
     *                   timestamp, so the phone's clock and time zone do not matter)
     */
    public record RiderLocationRequest(
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
            @PositiveOrZero @Max(5000) Integer accuracyM,
            @PositiveOrZero @Max(600) Integer ageSeconds) {
    }

    /**
     * @param shared             false when the fix was ignored because it is outside the plant area
     * @param sharedWith         who sees it, e.g. "S. Kumar · C-12"
     * @param outsideServiceArea the fix is farther from every pickup point than the admin's service radius
     */
    public record RiderLocationAckDto(
            boolean shared,
            String sharedWith,
            String pickupName,
            Integer metresToPickup,
            boolean atPickup,
            boolean outsideServiceArea,
            String message) {
    }
}
