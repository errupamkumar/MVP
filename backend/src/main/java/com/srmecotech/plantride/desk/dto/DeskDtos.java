package com.srmecotech.plantride.desk.dto;

import com.srmecotech.plantride.booking.BookingStatus;
import com.srmecotech.plantride.booking.RideType;
import com.srmecotech.plantride.masterdata.VehicleStatus;
import com.srmecotech.plantride.masterdata.dto.MasterDataDtos.DocState;
import com.srmecotech.plantride.safety.dto.AlertDto;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class DeskDtos {

    private DeskDtos() {
    }

    // ---------------------------------------------------------------- live board

    public record LiveBoardDto(
            LocalDateTime generatedAt,
            KpiDto kpis,
            List<VehicleLiveDto> vehicles,
            List<AlertDto> alerts,
            List<QueueItemDto> queue) {
    }

    public record KpiDto(
            int vehiclesOnTrip,
            int fleetSize,
            int seatOccupancyPct,
            BigDecimal avgWaitMinutes,
            int onTimePickupPct,
            int ridesToday,
            int openAlerts,
            int waitingBookings) {
    }

    public record VehicleLiveDto(
            Long id,
            String code,
            String registrationNo,
            String vehicleType,
            /** ON_TRIP, ASSIGNED, SHUTTLE, IDLE, OFF_DUTY, BLOCKED, OFF_ROAD. */
            String status,
            String statusLabel,
            /** OVERSPEED, DOC_EXPIRY, BREAKDOWN, SOS. */
            List<String> flags,
            String routeLabel,
            String locationLabel,
            int riders,
            int capacity,
            String driverName,
            String driverPhone,
            Long tripId,
            String tripCode,
            Long idleMinutes,
            LocalDateTime lastPingAt) {
    }

    public record QueueItemDto(
            Long bookingId,
            String bookingCode,
            BookingStatus status,
            RideType rideType,
            String riderName,
            String personnelNo,
            String costCentreCode,
            String routeCode,
            String fromStopName,
            String toStopName,
            int seats,
            LocalDateTime scheduledAt,
            LocalDateTime createdAt,
            long waitingMinutes,
            String vehicleCode,
            String note) {
    }

    public record CandidateDto(Long vehicleId, String vehicleCode, boolean feasible, Integer etaMinutes,
                               Integer seatsTaken, Integer capacity, Double score, String reason) {
    }

    public record DeskActionResultDto(String message, QueueItemDto booking) {
    }

    public record AssignRequest(@NotNull(message = "Choose a vehicle") Long vehicleId) {
    }

    public record ReasonRequest(@NotBlank(message = "Give a reason") @Size(max = 200) String reason) {
    }

    public record VehicleStatusRequest(@NotNull VehicleStatus status, @Size(max = 200) String note) {
    }

    // ---------------------------------------------------------------- admin

    public record UpdateRouteRulesRequest(
            @NotNull @Min(1) @Max(12) Integer maxPassengers,
            @NotNull @Min(1) @Max(30) Integer maxWaitMin,
            @NotNull @Min(0) @Max(60) Integer maxDetourMin,
            @NotNull @Min(5) @Max(80) Integer speedLimitKmh,
            @Min(5) @Max(240) Integer frequencyMin,
            /** The version the admin was looking at; a stale version is refused (409). */
            @NotNull Long version) {
    }

    public record RouteStopInput(
            @NotNull Long stopId,
            @NotNull @Min(0) @Max(120) Integer legMinutes,
            @NotNull @DecimalMin("0.0") @DecimalMax("50.0") BigDecimal legKm) {
    }

    public record ResequenceRequest(
            @NotNull Long version,
            @NotNull @Size(min = 2, max = 30, message = "A route needs 2 to 30 stops") List<@Valid @NotNull RouteStopInput> stops) {
    }

    public record ComplianceItemDto(
            String entityType,
            Long entityId,
            String code,
            String name,
            String document,
            String reference,
            LocalDate expiresOn,
            long daysLeft,
            DocState state,
            String label,
            boolean blocksDispatch) {
    }

    // ---------------------------------------------------------------- pickup points

    /**
     * A pickup point (stop) as the admin manages it.
     *
     * @param routes       labels of the routes whose sequence includes it
     * @param liveBookings bookings not yet closed that pick up or drop here
     */
    public record PickupPointDto(
            Long id,
            String code,
            String name,
            String zone,
            String landmark,
            double latitude,
            double longitude,
            int geofenceM,
            boolean active,
            List<String> routes,
            long liveBookings) {
    }

    /** Create or edit a pickup point. The code is fixed once created; it is ignored on edit. */
    public record SavePickupPointRequest(
            @Pattern(regexp = "[A-Z0-9-]{2,20}", message = "Code: 2 to 20 capital letters, digits or dashes") String code,
            @NotBlank(message = "Enter a name") @Size(max = 100) String name,
            @NotBlank(message = "Enter a zone") @Size(max = 20) String zone,
            @Size(max = 160, message = "Keep the landmark under 160 characters") String landmark,
            @NotNull(message = "Enter the latitude") @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @NotNull(message = "Enter the longitude") @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
            @NotNull @Min(value = 10, message = "Arrival radius is 10 to 500 m") @Max(value = 500, message = "Arrival radius is 10 to 500 m") Integer geofenceM,
            Boolean active) {
    }

    public record AuditLogDto(Long id, String actorName, String action, String entityType, Long entityId,
                              String details, LocalDateTime createdAt) {
    }
}
