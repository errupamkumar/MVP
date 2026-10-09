package com.srmecotech.plantride.booking.dto;

import com.srmecotech.plantride.booking.BookingStatus;
import com.srmecotech.plantride.booking.RideType;
import com.srmecotech.plantride.gps.dto.LiveLocationDtos.CabLocationDto;
import com.srmecotech.plantride.gps.dto.LiveLocationDtos.RiderLocationDto;
import com.srmecotech.plantride.masterdata.dto.MasterDataDtos.RouteRef;
import com.srmecotech.plantride.masterdata.dto.MasterDataDtos.StopRef;
import com.srmecotech.plantride.masterdata.dto.MasterDataDtos.VehicleRef;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class RiderDtos {

    private RiderDtos() {
    }

    public static final String PHONE_PATTERN = "^\\+?[0-9 ()-]{7,20}$";

    // ---------------------------------------------------------------- profile & home

    public record EmployeeProfileDto(
            Long employeeId,
            String personnelNo,
            String fullName,
            String email,
            String phone,
            String department,
            String grade,
            String costCentreCode,
            String costCentreName,
            boolean exclusiveEligible,
            /** True when the no-show rule has paused booking for this rider. */
            boolean bookingPaused,
            long noShowsInWindow) {
    }

    public record SavedPlaceDto(Long stopId, String stopName, String hint, long trips) {
    }

    /**
     * @param firstRunAt set (e.g. "06:00") when the shuttle has not started for the day yet;
     *                   minutesToNext then counts down to that first run
     */
    public record ShuttleInfoDto(String vehicleCode, String routeLabel, Long stopId, String stopName,
                                 int minutesToNext, int frequencyMin, int seatCapacity, String firstRunAt) {
    }

    public record RiderHomeDto(
            EmployeeProfileDto profile,
            BookingDto activeBooking,
            long upcomingCount,
            List<SavedPlaceDto> savedPlaces,
            Long defaultFromStopId,
            ShuttleInfoDto nextShuttle,
            long unreadNotifications) {
    }

    // ---------------------------------------------------------------- ride options

    public record RideOptionsRequest(
            @NotNull(message = "Choose a pickup stop") Long fromStopId,
            @NotNull(message = "Choose a drop stop") Long toStopId,
            @Min(1) @Max(12) Integer seats,
            LocalDateTime scheduledAt) {
    }

    public record RideOptionDto(
            /** SHUTTLE (information only), SHARED or EXCLUSIVE. */
            String type,
            boolean bookable,
            String title,
            String subtitle,
            Integer etaMinutes,
            Integer seatsTaken,
            Integer seatsFree,
            Integer capacity,
            String vehicleCode,
            boolean requiresApproval,
            String note) {
    }

    public record RideOptionsResponse(
            RouteRef route,
            StopRef from,
            StopRef to,
            BigDecimal rideKm,
            int rideMinutes,
            int occupancyCap,
            String costCentreCode,
            String costCentreName,
            LocalDateTime scheduledAt,
            List<RideOptionDto> options) {
    }

    // ---------------------------------------------------------------- availability

    public record AvailabilityRequest(
            @NotNull(message = "Choose a pickup stop") Long fromStopId,
            @NotNull(message = "Choose a drop stop") Long toStopId,
            @Min(1) @Max(12) Integer seats) {
    }

    /** A pickup point as the rider sees it on the ground. */
    public record SpotDto(Long id, String code, String name, String zone, String landmark) {
    }

    /**
     * One way a ride can reach the pickup: a cab that is free now, a busy cab
     * once it has finished its current run, or the shuttle's next timetabled run.
     *
     * @param kind       CAB or SHUTTLE (the shuttle is walk-up, never booked)
     * @param inMinutes  minutes until it can be at the pickup
     * @param distanceKm road distance from where the vehicle is now, when known
     */
    public record NextRideDto(
            String kind,
            String vehicleCode,
            String registrationNo,
            String driverName,
            int inMinutes,
            LocalDateTime arrivesAt,
            Double distanceKm,
            boolean availableNow,
            String note) {
    }

    /**
     * A vehicle on duty, nearest first. Plant Ride is an internal service, so
     * riders see the driver and phone of every cab that could come for them.
     *
     * @param status             FREE, ON_TRIP, FULL, SHUTTLE, OFF_ROAD, BLOCKED or UNAVAILABLE
     * @param canTakeYou         the engine would assign it to this ride now
     * @param etaMinutes         to the pickup, when it can take the rider now
     * @param availableInMinutes when a busy cab could be at the pickup, if it will be at all
     */
    public record NearbyCabDto(
            Long vehicleId,
            String vehicleCode,
            String registrationNo,
            String vehicleType,
            String fleet,
            String driverName,
            String driverPhone,
            String status,
            String statusLabel,
            String at,
            Double distanceKm,
            int seatsTaken,
            int capacity,
            boolean canTakeYou,
            Integer etaMinutes,
            Integer availableInMinutes,
            String note) {
    }

    /** An admin-configured pickup point within walking distance where a ride comes sooner. */
    public record AlternativeSpotDto(
            SpotDto stop,
            RouteRef route,
            int walkMetres,
            int walkMinutes,
            int rideInMinutes,
            int readyInMinutes,
            String vehicleCode,
            boolean availableNow,
            boolean sooner,
            String note) {
    }

    /**
     * How a waiting request is being searched. Durations are in seconds from
     * {@code checkedAt}, so the app can count down with its own clock whatever
     * the phone's time zone.
     */
    public record SearchStatusDto(
            Long bookingId,
            String bookingCode,
            long searchingForSeconds,
            Long lastCheckedSecondsAgo,
            long nextCheckInSeconds,
            long checkEverySeconds,
            long expiresInSeconds,
            LocalDateTime expiresAt,
            String lastResult) {
    }

    public record AvailabilityDto(
            LocalDateTime checkedAt,
            RouteRef route,
            SpotDto pickup,
            SpotDto drop,
            int vehiclesOnDuty,
            boolean availableNow,
            String summary,
            NextRideDto nextAtPickup,
            List<NextRideDto> upcomingAtPickup,
            List<NearbyCabDto> nearbyCabs,
            List<AlternativeSpotDto> alternativeSpots,
            SearchStatusDto search) {
    }

    // ---------------------------------------------------------------- booking

    public record CreateBookingRequest(
            @NotNull(message = "Choose a pickup stop") Long fromStopId,
            @NotNull(message = "Choose a drop stop") Long toStopId,
            @NotNull(message = "Choose a ride type") RideType rideType,
            @NotNull @Min(1) @Max(12) Integer seats,
            LocalDateTime scheduledAt,
            @NotBlank(message = "Enter the rider's name") @Size(max = 100) String riderName,
            @NotBlank(message = "Enter a contact number")
            @Pattern(regexp = PHONE_PATTERN, message = "Enter a valid phone number") String riderPhone,
            @Email(message = "Enter a valid email") @Size(max = 120) String riderEmail,
            boolean forGuest) {
    }

    /** @param fleet "LHS own fleet" or the vendor's name */
    public record DriverContactDto(String name, String phone, String fleet) {
    }

    public record ProgressStopDto(int seq, String stopName, double latitude, double longitude, boolean pickup,
                                  boolean drop, /** PASSED, HERE, NEXT or AHEAD. */ String state) {
    }

    public record RideProgressDto(
            /** SCHEDULED, AWAITING_APPROVAL, FINDING_CAB, ON_THE_WAY, AT_PICKUP, ON_TRIP, COMPLETED, CLOSED. */
            String stage,
            String stageLabel,
            Integer etaToPickupMinutes,
            Integer etaToDropMinutes,
            String vehicleAt,
            int coRiders,
            int seatsTaken,
            int capacity,
            List<ProgressStopDto> stops) {
    }

    public record BookingDto(
            Long id,
            String bookingCode,
            BookingStatus status,
            RideType rideType,
            int seats,
            boolean forGuest,
            String riderName,
            String riderPhone,
            String riderEmail,
            RouteRef route,
            StopRef fromStop,
            StopRef toStop,
            LocalDateTime scheduledAt,
            LocalDateTime createdAt,
            LocalDateTime assignedAt,
            LocalDateTime boardedAt,
            LocalDateTime droppedAt,
            LocalDateTime cancelledAt,
            String cancelReason,
            /** Shown to the rider only while the booking is live. */
            String otp,
            String trackingToken,
            String trackingUrl,
            VehicleRef vehicle,
            DriverContactDto driver,
            /** Where the cab is right now, measured to the pickup (or the drop once on board). */
            CabLocationDto cabLocation,
            /** What the rider is sharing with the driver while the cab is on its way; null when nothing is. */
            RiderLocationDto myLocation,
            String tripCode,
            Integer promisedEtaMinutes,
            LocalDateTime promisedPickupAt,
            RideProgressDto progress,
            String costCentreCode,
            String costCentreName,
            BigDecimal rideKm,
            int rideMinutes,
            BigDecimal distanceKm,
            BigDecimal costAmount,
            String matchNote,
            Integer rating,
            List<String> ratingTags,
            String ratingNote,
            boolean canCancel,
            boolean canRate) {
    }

    public record BookingSummaryDto(
            Long id,
            String bookingCode,
            BookingStatus status,
            RideType rideType,
            String fromStopName,
            String toStopName,
            LocalDateTime when,
            BigDecimal distanceKm,
            String vehicleCode,
            BigDecimal costAmount,
            String cancelReason,
            Integer rating) {
    }

    public record CancelBookingRequest(@Size(max = 200) String reason) {
    }

    public enum RatingTag {
        ON_TIME,
        CLEAN_CAB,
        SAFE_DRIVING,
        LONG_WAIT,
        HARD_TO_FIND
    }

    public record RateBookingRequest(
            @NotNull(message = "Pick a rating") @Min(1) @Max(5) Integer rating,
            @Size(max = 5) List<@NotNull RatingTag> tags,
            @Size(max = 500) String note) {
    }

    public record RiderSosRequest(Long bookingId, Double latitude, Double longitude, @Size(max = 300) String note) {
    }

    public record SosResultDto(Long alertId, String message) {
    }

    // ---------------------------------------------------------------- public tracking

    /** The shareable link's view: the vehicle and its progress, never the people in it. */
    public record TrackingDto(
            String token,
            String stage,
            String stageLabel,
            String vehicleCode,
            String registrationNo,
            String routeLabel,
            String fromStopName,
            String toStopName,
            String vehicleAt,
            Integer etaToPickupMinutes,
            Integer etaToDropMinutes,
            List<ProgressStopDto> stops,
            LocalDateTime updatedAt) {
    }
}
