package com.srmecotech.plantride.trip;

import com.srmecotech.plantride.masterdata.Driver;
import com.srmecotech.plantride.masterdata.Route;
import com.srmecotech.plantride.masterdata.Vehicle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One vehicle running one route forwards, carrying pooled riders up to the
 * occupancy cap. Position is tracked as the last route position reached
 * ({@code currentSeq}); null means the cab is still heading to its first
 * pickup.
 */
@Entity
@Table(name = "trip")
@Getter
@Setter
@NoArgsConstructor
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_code", nullable = false, unique = true, length = 20)
    private String tripCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "duty_id")
    private Duty duty;

    @Column(name = "exclusive_ride", nullable = false)
    private boolean exclusiveRide;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 12)
    private TripStatus status;

    /** First route position this trip serves. */
    @Column(name = "start_seq", nullable = false)
    private int startSeq;

    /** Last route position the cab arrived at; null while heading to the first pickup. */
    @Column(name = "current_seq")
    private Integer currentSeq;

    @Column(name = "last_arrived_at")
    private LocalDateTime lastArrivedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "distance_km", precision = 7, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "cost_amount", precision = 10, scale = 2)
    private BigDecimal costAmount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Version
    private long version;

    public boolean isOpen() {
        return TripStatus.OPEN.contains(status);
    }
}
