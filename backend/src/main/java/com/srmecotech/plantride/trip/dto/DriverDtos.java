package com.srmecotech.plantride.trip.dto;

import com.srmecotech.plantride.masterdata.dto.MasterDataDtos.DocumentStatus;
import com.srmecotech.plantride.masterdata.dto.MasterDataDtos.RouteRef;
import com.srmecotech.plantride.masterdata.dto.MasterDataDtos.VehicleRef;
import com.srmecotech.plantride.trip.DutyStatus;
import com.srmecotech.plantride.trip.TripStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class DriverDtos {

    private DriverDtos() {
    }

    /** Items the start-of-duty check must tick before the queue unlocks. */
    public static final List<String> REQUIRED_CHECKS = List.of("TYRES", "LIGHTS", "BELTS", "FUEL", "FIRST_AID");

    // ---------------------------------------------------------------- profile & duty

    public record DriverProfileDto(
            Long driverId,
            String driverCode,
            String fullName,
            String phone,
            String vendorName,
            String gatesAllowed,
            int maxDutyMinutes,
            List<DocumentStatus> documents,
            /** Set when an expired licence or gate pass blocks duty. */
            String blockedReason,
            Long defaultVehicleId,
            DutyDto duty) {
    }

    public record VehicleOptionDto(
            Long id,
            String code,
            String registrationNo,
            String vehicleType,
            int seatCapacity,
            String vendorName,
            int odometerKm,
            String currentStopName,
            boolean available,
            /** Why the vehicle cannot be taken (expired document, off-road, on duty with someone else). */
            String blockedReason,
            List<DocumentStatus> documents) {
    }

    public record StartDutyRequest(
            @NotNull(message = "Choose a vehicle") Long vehicleId,
            @NotNull(message = "Enter the odometer reading") @PositiveOrZero @Max(9_999_999) Integer startOdometer,
            @NotEmpty(message = "Complete the vehicle check") List<@NotBlank String> checklist) {
    }

    public record EndDutyRequest(
            @NotNull(message = "Enter the closing odometer reading") @PositiveOrZero @Max(9_999_999) Integer endOdometer,
            @DecimalMin(value = "0.0") @DecimalMax(value = "300.0") BigDecimal fuelLitres) {
    }

    public record DutyDto(
            Long id,
            DutyStatus status,
            VehicleRef vehicle,
            LocalDateTime startedAt,
            long minutesOnDuty,
            int maxDutyMinutes,
            int startOdometer,
            int tripsCompleted,
            int ridersCarried,
            BigDecimal distanceKm) {
    }

    public record DutySummaryDto(
            Long dutyId,
            String vehicleCode,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            long dutyMinutes,
            int tripsCompleted,
            int ridersCarried,
            BigDecimal gpsKm,
            int odometerKm,
            /** MATCHES when the odometer agrees with GPS distance (allowing for runs between trips), else REVIEW. */
            String odometerCheck,
            BigDecimal fuelLitres) {
    }

    // ---------------------------------------------------------------- trip queue

    public record TripQueueDto(
            Long tripId,
            String tripCode,
            TripStatus status,
            RouteRef route,
            boolean exclusive,
            int capacity,
            int onBoard,
            int seatsLeft,
            Integer currentSeq,
            String currentStopName,
            QueueStopDto nextStop,
            boolean canArriveNext,
            String arriveBlockedReason,
            int speedLimitKmh,
            List<QueueStopDto> stops) {
    }

    public record QueueStopDto(
            int seq,
            Long stopId,
            String stopName,
            /** DONE, CURRENT, NEXT or UPCOMING. */
            String state,
            int etaMinutes,
            String summary,
            List<QueueRiderDto> pickups,
            List<QueueRiderDto> drops) {
    }

    public record QueueRiderDto(
            Long bookingId,
            String bookingCode,
            String riderName,
            String personnelNo,
            String riderPhone,
            int seats,
            String status,
            String fromStopName,
            String toStopName,
            /** Seconds until the driver may mark a no-show (0 = allowed now); null if not at the pickup. */
            Long noShowAllowedInSeconds,
            boolean otpLocked) {
    }

    /** The driver's current trip, or trip = null when the queue is empty (e.g. the last drop just closed it). */
    public record DriverQueueResponse(TripQueueDto trip, String notice) {
    }

    public record ArriveRequest(@NotNull @Min(1) Integer stopSeq) {
    }

    public record BoardRequest(
            @NotNull Long bookingId,
            @NotBlank @Pattern(regexp = "\\d{4}", message = "The OTP is 4 digits") String otp) {
    }

    public record NoShowRequest(@NotNull Long bookingId) {
    }

    // ---------------------------------------------------------------- GPS & issues

    public record LocationPingRequest(
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
            @PositiveOrZero @DecimalMax("250.0") Double speedKmh,
            LocalDateTime recordedAt) {
    }

    public record LocationAckDto(boolean overspeed, int speedLimitKmh, String autoArrivedAt) {
    }

    public enum IssueType {
        BREAKDOWN,
        ACCIDENT,
        DELAY,
        FUEL,
        OTHER
    }

    public record ReportIssueRequest(
            @NotNull IssueType type,
            @NotBlank(message = "Say what happened") @Size(max = 400) String description,
            @DecimalMin("0.0") @DecimalMax("300.0") BigDecimal litres) {
    }

    public record IssueResultDto(Long alertId, String message, int ridersRematched) {
    }

    public record SosRequest(Double latitude, Double longitude, @Size(max = 300) String note) {
    }
}
