package com.srmecotech.ecoride.ride;

import com.srmecotech.ecoride.pricing.FareBreakdown;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** What the rider and driver apps poll. Seconds-left values are computed on the server, so phone clocks do not matter. */
public final class RideViews {

    private RideViews() {
    }

    public record Place(double lat, double lng, String address) {
    }

    /**
     * @param ageS seconds since the fix (a large value means the signal was lost)
     */
    public record Position(double lat, double lng, Integer heading, Double speedKmh, Integer ageS) {
    }

    public record Vehicle(String type, String typeName, String make, String model, String color, String plate) {
    }

    public record DriverCard(long id, String name, String phone, BigDecimal rating, int ratingCount, long trips,
                             Vehicle vehicle, Position position) {
    }

    public record RiderCard(long id, String name, String phone, BigDecimal rating, int ratingCount, Position position) {
    }

    /**
     * Dynamic distances, recomputed from the latest GPS fix on every poll.
     *
     * @param toPickupM          road distance left from the cab to the pickup (on the way)
     * @param toPickupEtaS       time for it
     * @param remainingM         road distance left to the drop (trip in progress)
     * @param remainingEtaS      time for it
     * @param travelledM         GPS distance driven with the rider on board
     * @param progress           0..1 along the current leg
     * @param offRoute           the cab is more than 250 m from the planned route
     * @param waitingS           seconds since the driver arrived
     * @param freeWaitLeftS      free waiting left before waiting charges start
     * @param riderToPickupM     the rider's own distance from the pickup, when shared
     */
    public record Live(Integer toPickupM, Integer toPickupEtaS, Integer remainingM, Integer remainingEtaS,
                       int travelledM, double progress, boolean offRoute, Integer waitingS, Integer freeWaitLeftS,
                       Integer riderToPickupM) {
    }

    /**
     * @param driversContacted how many drivers have seen the request so far
     * @param offerExpiresInS  seconds left for the driver currently looking at it
     */
    public record Search(int elapsedS, int timeoutS, int driversContacted, Integer offerExpiresInS) {
    }

    /**
     * @param quote  the upfront fare accepted at booking
     * @param fare   the final fare (null until completed)
     * @param policy UPFRONT or RECALCULATED
     */
    public record Fare(FareBreakdown quote, FareBreakdown fare, String policy, BigDecimal total, PaymentMethod method,
                       PaymentStatus paymentStatus, BigDecimal tip, BigDecimal cancellationFee) {
    }

    public record TimelineEntry(String type, String actor, String message, Instant at) {
    }

    public record RiderRide(
            long id,
            String code,
            RideStatus status,
            String vehicleType,
            String vehicleTypeName,
            Place pickup,
            Place drop,
            int estDistanceM,
            int estDurationS,
            String routePolyline,
            String pickupPolyline,
            String otp,
            DriverCard driver,
            Live live,
            Search search,
            Fare fare,
            int actualDistanceM,
            Instant requestedAt,
            Instant acceptedAt,
            Instant arrivedAt,
            Instant startedAt,
            Instant completedAt,
            Instant cancelledAt,
            String cancelledBy,
            String cancelReason,
            Integer myRating,
            boolean cancelFeeApplies,
            Integer freeCancelLeftS,
            BigDecimal cancelFee,
            List<TimelineEntry> timeline) {
    }

    public record DriverRide(
            long id,
            String code,
            RideStatus status,
            String vehicleType,
            Place pickup,
            Place drop,
            int estDistanceM,
            int estDurationS,
            String routePolyline,
            String pickupPolyline,
            RiderCard rider,
            Live live,
            Fare fare,
            BigDecimal estEarning,
            int actualDistanceM,
            int pickupTravelM,
            Instant acceptedAt,
            Instant arrivedAt,
            Instant startedAt,
            Instant completedAt,
            Instant cancelledAt,
            String cancelledBy,
            String cancelReason,
            Integer riderRating,
            boolean canCancelAsNoShow,
            Integer noShowInS) {
    }

    /** The full-screen request card with its countdown. */
    public record Offer(
            long offerId,
            long rideId,
            int expiresInS,
            int timeoutS,
            String vehicleType,
            Place pickup,
            Place drop,
            int pickupDistanceM,
            int pickupEtaS,
            int tripDistanceM,
            int tripDurationS,
            BigDecimal fare,
            BigDecimal estEarning,
            PaymentMethod paymentMethod,
            BigDecimal surgeMultiplier,
            String riderName,
            BigDecimal riderRating,
            String routePolyline) {
    }
}
