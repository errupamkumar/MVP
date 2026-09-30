package com.srmecotech.plantride.safety.dto;

import com.srmecotech.plantride.safety.Alert;
import com.srmecotech.plantride.safety.AlertSeverity;
import com.srmecotech.plantride.safety.AlertStatus;
import com.srmecotech.plantride.safety.AlertType;

import java.time.Duration;
import java.time.LocalDateTime;

public record AlertDto(
        Long id,
        AlertType type,
        AlertSeverity severity,
        AlertStatus status,
        String message,
        String vehicleCode,
        String bookingCode,
        String raisedBy,
        Double latitude,
        Double longitude,
        LocalDateTime createdAt,
        long minutesAgo,
        String acknowledgedBy,
        String resolvedBy) {

    public static AlertDto of(Alert a, LocalDateTime now) {
        return new AlertDto(
                a.getId(), a.getType(), a.getSeverity(), a.getStatus(), a.getMessage(),
                a.getVehicle() == null ? null : a.getVehicle().getCode(),
                a.getBooking() == null ? null : a.getBooking().getBookingCode(),
                a.getRaisedBy() == null ? "system" : a.getRaisedBy().getFullName(),
                a.getLatitude(), a.getLongitude(), a.getCreatedAt(),
                Math.max(0, Duration.between(a.getCreatedAt(), now).toMinutes()),
                a.getAcknowledgedBy(), a.getResolvedBy());
    }
}
