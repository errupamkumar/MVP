package com.srmecotech.plantride.gps;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A waiting rider's latest position, shared with the driver coming for them.
 * A table of its own (not booking columns) so that a ping every few seconds
 * never bumps the booking's version and makes the driver's Board tap stale.
 */
@Entity
@Table(name = "rider_location")
@Getter
@Setter
@NoArgsConstructor
public class RiderLocation {

    @Id
    @Column(name = "booking_id")
    private Long bookingId;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(name = "accuracy_m")
    private Integer accuracyM;

    /** When the phone took the fix (received time minus its reported age). */
    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;
}
