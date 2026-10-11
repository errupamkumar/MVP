package com.srmecotech.ecoride.ride;

import com.srmecotech.ecoride.driver.DriverDtos;
import com.srmecotech.ecoride.geo.LocationInput;
import com.srmecotech.ecoride.pricing.FareQuote;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Request and response bodies of the rider and driver ride endpoints. */
public final class RideDtos {

    private RideDtos() {
    }

    // ------------------------------------------------------------------ rider

    public record QuoteRequest(@NotNull @Valid LocationInput pickup, @NotNull @Valid LocationInput drop,
                               @Size(max = 16) String promoCode) {
    }

    public record RouteInfo(int distanceM, int durationS, String polyline, String provider) {
    }

    public record SurgeInfo(BigDecimal multiplier, String zone, String mode) {
    }

    public record PromoInfo(String code, boolean valid, String message) {
    }

    public record QuoteResponse(String quoteId, Instant expiresAt, int validForS, RideViews.Place pickup,
                                RideViews.Place drop, RouteInfo route, SurgeInfo surge, PromoInfo promo,
                                List<FareQuote.QuoteOption> options) {
    }

    public record RideRequest(@NotBlank String quoteId, @NotBlank String vehicleType, @NotNull PaymentMethod paymentMethod) {
    }

    public record CurrentRide(RideViews.RiderRide ride) {
    }

    public record CancelRequest(@Size(max = 200) String reason) {
    }

    /** A phone fix. {@code simulated} marks fixes replayed by the demo GPS simulator. */
    public record LocationPing(
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng,
            @Min(0) @Max(359) Integer heading,
            @DecimalMin("0") @DecimalMax("300") Double speedKmh,
            @Min(0) @Max(5000) Integer accuracyM,
            Boolean simulated) {
    }

    public record RateDriverRequest(
            @NotNull @Min(1) @Max(5) Integer stars,
            @Size(max = 6) List<@Size(max = 30) String> tags,
            @Size(max = 200) String comment,
            @DecimalMin("0") @DecimalMax("500") BigDecimal tip) {
    }

    public record SosRequest(Double lat, Double lng, @Size(max = 200) String note) {
    }

    public record NearbyCar(String vehicleType, double lat, double lng, Integer heading) {
    }

    /** Cars around the rider's map (no driver identity), and how soon the nearest of each type could come. */
    public record Nearby(List<NearbyCar> cars, Map<String, Integer> etaByType) {
    }

    public record RideSummary(long id, String code, RideStatus status, String vehicleType, String vehicleTypeName,
                              String pickupAddress, String dropAddress, Instant requestedAt, Instant completedAt,
                              BigDecimal total, PaymentMethod paymentMethod, String counterpartName, Integer rating,
                              double distanceKm) {
    }

    // ------------------------------------------------------------------ driver

    public record OnlineRequest(@DecimalMin("-90") @DecimalMax("90") Double lat,
                                @DecimalMin("-180") @DecimalMax("180") Double lng) {
    }

    public record StartRequest(@NotBlank @Pattern(regexp = "\\d{4}", message = "Enter the 4-digit code") String otp) {
    }

    public record RateRiderRequest(@NotNull @Min(1) @Max(5) Integer stars) {
    }

    public record Today(BigDecimal earnings, int trips, Integer onlineMinutes) {
    }

    /**
     * Everything the driver app shows, in one poll.
     *
     * @param offer    a ride request waiting for an answer, with its countdown
     * @param ride     the assigned ride (on the way, waiting, or on trip)
     * @param lastTrip the trip just finished, while cash is still to be collected or the rider unrated
     */
    public record DriverState(DriverDtos.Profile profile, RideViews.Position position, Today today,
                              RideViews.Offer offer, RideViews.DriverRide ride, RideViews.DriverRide lastTrip) {
    }
}
