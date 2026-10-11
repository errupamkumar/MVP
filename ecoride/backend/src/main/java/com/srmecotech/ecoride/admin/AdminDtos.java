package com.srmecotech.ecoride.admin;

import com.srmecotech.ecoride.driver.DriverLedgerService;
import com.srmecotech.ecoride.pricing.FareBreakdown;
import com.srmecotech.ecoride.ride.RideLog;
import com.srmecotech.ecoride.ride.RideViews;
import com.srmecotech.ecoride.rider.RiderDtos;
import com.srmecotech.ecoride.rider.WalletService;
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
import java.time.LocalDate;
import java.util.List;

public final class AdminDtos {

    private AdminDtos() {
    }

    // ------------------------------------------------------------------ account

    public record LoginRequest(@NotBlank(message = "Enter your email") String email,
                               @NotBlank(message = "Enter your password") String password) {
    }

    public record AdminProfile(long id, String name, String email) {
    }

    public record AuthResponse(String token, Instant expiresAt, AdminProfile admin) {
    }

    public record PageDto<T>(List<T> items, int page, int size, long total) {
    }

    // ------------------------------------------------------------------ dashboard

    public record Kpis(int ridesToday, int completedToday, int cancelledToday, int activeNow, int searchingNow,
                       int onlineDrivers, int onTripDrivers, int approvedDrivers, int pendingDrivers, int riders,
                       int newRidersToday, BigDecimal gmvToday, BigDecimal commissionToday, BigDecimal avgFareToday,
                       Double avgPickupMinToday, Double cancelRateToday, Double avgRatingToday, int openSos) {
    }

    public record DayPoint(LocalDate date, int rides, int completed, int cancelled, BigDecimal gmv, BigDecimal commission) {
    }

    public record HourPoint(int hour, int rides) {
    }

    public record TypeSplit(String vehicleType, String name, int rides, BigDecimal gmv) {
    }

    public record Dashboard(Kpis kpis, List<DayPoint> days, List<HourPoint> hours, List<TypeSplit> byType,
                            List<RideRow> recent, List<SosRow> openSos, List<DriverRow> pendingDrivers) {
    }

    // ------------------------------------------------------------------ live map

    public record LiveDriver(long id, String name, String vehicleType, String plate, String availability, double lat,
                             double lng, Integer heading, Integer ageS, boolean simulated, Long rideId, String rideCode,
                             String rideStatus, boolean hasOffer) {
    }

    public record LiveRide(long id, String code, String status, String vehicleType, String riderName, String driverName,
                           RideViews.Place pickup, RideViews.Place drop, Double cabLat, Double cabLng,
                           String routePolyline, String pickupPolyline, boolean simulated, Instant requestedAt) {
    }

    public record LiveZone(long id, String name, double lat, double lng, int radiusM, String mode,
                           BigDecimal multiplier, boolean active, Integer demand, Integer supply) {
    }

    public record Live(List<LiveDriver> drivers, List<LiveRide> rides, List<LiveZone> zones, int online, int onTrip,
                       int searching, int inProgress) {
    }

    // ------------------------------------------------------------------ rides

    public record RideRow(long id, String code, String status, String vehicleType, String riderName, String driverName,
                          String pickupAddress, String dropAddress, Instant requestedAt, Instant completedAt,
                          BigDecimal fare, String paymentMethod, String paymentStatus, double distanceKm,
                          boolean simulated) {
    }

    public record OfferRow(long driverId, String driverName, String status, int distanceM, int etaS, Instant offeredAt,
                           Instant respondedAt, boolean simulated) {
    }

    public record PaymentRow(long id, long rideId, String rideCode, String riderName, String kind, String method,
                             BigDecimal amount, String status, String reference, Instant createdAt) {
    }

    public record SosRow(long id, long rideId, String rideCode, String raisedBy, String riderName, String driverName,
                         Double lat, Double lng, String status, String note, Instant createdAt, Instant resolvedAt,
                         String resolvedBy) {
    }

    public record PersonBrief(long id, String name, String phone, BigDecimal rating, String extra) {
    }

    public record RideDetail(
            RideRow row,
            RideViews.Place pickup,
            RideViews.Place drop,
            String otp,
            int estDistanceM,
            int estDurationS,
            int actualDistanceM,
            int pickupTravelM,
            String routePolyline,
            String pickupPolyline,
            String routeProvider,
            BigDecimal surgeMultiplier,
            String promoCode,
            FareBreakdown quote,
            FareBreakdown fare,
            String farePolicy,
            BigDecimal commission,
            BigDecimal driverEarning,
            BigDecimal tip,
            BigDecimal cancellationFee,
            String cancelledBy,
            String cancelReason,
            Integer driverRating,
            String driverFeedback,
            Integer riderRating,
            Instant acceptedAt,
            Instant arrivedAt,
            Instant startedAt,
            Instant cancelledAt,
            PersonBrief rider,
            PersonBrief driver,
            List<RideViews.TimelineEntry> timeline,
            List<OfferRow> offers,
            List<RideLog.TrackPoint> track,
            List<PaymentRow> payments,
            List<SosRow> sos) {
    }

    // ------------------------------------------------------------------ people

    public record DriverRow(long id, String name, String phone, String vehicleType, String vehicle, String plate,
                            String status, String availability, BigDecimal rating, int ratingCount,
                            Integer acceptanceRate, long trips, BigDecimal balance, boolean simulated,
                            Instant createdAt, Instant locatedAt) {
    }

    public record DriverDetail(DriverRow row, String email, String licenseNo, int vehicleYear, String statusReason,
                               Instant approvedAt, Instant lastLoginAt, Double lat, Double lng,
                               BigDecimal earnedThisWeek, int tripsThisWeek, List<RideRow> recentRides,
                               List<DriverLedgerService.LedgerLine> ledger) {
    }

    public record RiderRow(long id, String name, String phone, String email, String status, BigDecimal rating,
                           int ratingCount, BigDecimal wallet, long rides, boolean simulated, Instant createdAt,
                           Instant lastLoginAt) {
    }

    public record RiderDetail(RiderRow row, String statusReason, List<RideRow> recentRides,
                              List<WalletService.WalletLine> wallet, List<RiderDtos.SavedPlaceDto> places) {
    }

    public record ReasonRequest(@NotBlank(message = "Give a reason") @Size(max = 200) String reason) {
    }

    public record AmountRequest(
            @NotNull @DecimalMin(value = "1", message = "At least ₹1") @DecimalMax(value = "10000", message = "At most ₹10,000") BigDecimal amount,
            @NotBlank(message = "Add a note") @Size(max = 120) String note) {
    }

    // ------------------------------------------------------------------ configuration

    public record VehicleTypeDto(String code, String name, String description, int capacity, BigDecimal baseFare,
                                 BigDecimal baseKm, BigDecimal perKm, BigDecimal perMin, BigDecimal minFare,
                                 int waitFreeMin, BigDecimal waitPerMin, BigDecimal cancelFee, int sortOrder,
                                 boolean active, Instant updatedAt) {
    }

    public record VehicleTypeUpdate(
            @NotBlank @Size(max = 40) String name,
            @NotBlank @Size(max = 120) String description,
            @NotNull @Min(1) @Max(8) Integer capacity,
            @NotNull @DecimalMin("0") @DecimalMax("5000") BigDecimal baseFare,
            @NotNull @DecimalMin("0") @DecimalMax("20") BigDecimal baseKm,
            @NotNull @DecimalMin("0") @DecimalMax("500") BigDecimal perKm,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal perMin,
            @NotNull @DecimalMin("0") @DecimalMax("5000") BigDecimal minFare,
            @NotNull @Min(0) @Max(30) Integer waitFreeMin,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal waitPerMin,
            @NotNull @DecimalMin("0") @DecimalMax("1000") BigDecimal cancelFee,
            boolean active) {
    }

    public record FarePreviewRequest(
            @NotBlank String vehicleType,
            @NotNull @DecimalMin("0.1") @DecimalMax("200") Double distanceKm,
            @NotNull @Min(1) @Max(600) Integer minutes,
            @DecimalMin("1.0") @DecimalMax("3.0") BigDecimal surge,
            @Min(0) @Max(60) Integer waitingMin,
            @Size(max = 16) String promoCode) {
    }

    public record SurgeZoneDto(long id, String name, double lat, double lng, int radiusM, String mode,
                               BigDecimal manualMultiplier, boolean active, BigDecimal currentMultiplier, Integer demand,
                               Integer supply, Instant updatedAt) {
    }

    public record SurgeZoneRequest(
            @NotBlank @Size(max = 60) String name,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng,
            @NotNull @Min(300) @Max(20000) Integer radiusM,
            @NotBlank @Pattern(regexp = "AUTO|MANUAL") String mode,
            @NotNull @DecimalMin("1.0") @DecimalMax("3.0") BigDecimal manualMultiplier,
            boolean active) {
    }

    public record PromoDto(String code, String description, Integer percentOff, BigDecimal flatOff,
                           BigDecimal maxDiscount, BigDecimal minFare, int usesPerRider, LocalDate validUntil,
                           boolean active, int totalUses, Instant createdAt) {
    }

    public record PromoRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9]{3,16}", message = "3-16 letters or digits") String code,
            @NotBlank @Size(max = 120) String description,
            @Min(1) @Max(100) Integer percentOff,
            @DecimalMin("1") @DecimalMax("1000") BigDecimal flatOff,
            @DecimalMin("1") @DecimalMax("1000") BigDecimal maxDiscount,
            @NotNull @DecimalMin("0") BigDecimal minFare,
            @NotNull @Min(1) @Max(100) Integer usesPerRider,
            LocalDate validUntil,
            boolean active) {
    }

    public record SettingDto(String key, String category, String label, String description, String type, String value,
                             String defaultValue, double min, double max, String unit) {
    }

    public record SettingUpdate(@NotBlank String value) {
    }

    // ------------------------------------------------------------------ money

    public record FinanceTotals(int trips, BigDecimal gmv, BigDecimal tax, BigDecimal discounts, BigDecimal commission,
                                BigDecimal driverEarnings, BigDecimal tips, BigDecimal cancellationFees,
                                BigDecimal platformNet, BigDecimal cash, BigDecimal digital) {
    }

    public record MethodSplit(String method, int trips, BigDecimal amount) {
    }

    public record PayoutRow(long driverId, String name, String plate, BigDecimal balance, int tripsThisWeek,
                            BigDecimal earnedThisWeek, boolean simulated) {
    }

    public record Finance(int days, FinanceTotals totals, List<DayPoint> daily, List<MethodSplit> byMethod,
                          List<TypeSplit> byType, List<PayoutRow> payouts) {
    }
}
