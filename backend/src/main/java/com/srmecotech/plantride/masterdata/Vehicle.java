package com.srmecotech.plantride.masterdata;

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
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "vehicle")
@Getter
@Setter
@NoArgsConstructor
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Fleet code painted on the cab, e.g. C-12. */
    @Column(nullable = false, unique = true, length = 10)
    private String code;

    @Column(name = "registration_no", nullable = false, unique = true, length = 20)
    private String registrationNo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "vehicle_type", nullable = false, length = 10)
    private VehicleType vehicleType;

    /** Passenger seats, excluding the driver. */
    @Column(name = "seat_capacity", nullable = false)
    private int seatCapacity;

    /** Null for the LHS own fleet. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id")
    private Vendor vendor;

    /** Rate card applied to GPS kilometres (vendor rate, or own-fleet cost per km). */
    @Column(name = "cost_per_km", nullable = false, precision = 8, scale = 2)
    private BigDecimal costPerKm;

    @Column(name = "insurance_policy_no", nullable = false, length = 40)
    private String insurancePolicyNo;

    @Column(name = "insurance_expiry", nullable = false)
    private LocalDate insuranceExpiry;

    @Column(name = "fitness_expiry", nullable = false)
    private LocalDate fitnessExpiry;

    @Column(name = "puc_expiry", nullable = false)
    private LocalDate pucExpiry;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private VehicleStatus status;

    /** For shuttles: the loop it runs. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "home_route_id")
    private Route homeRoute;

    /** Last stop the vehicle was confirmed at (arrival or geofence). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_stop_id")
    private Stop currentStop;

    @Column(name = "last_latitude")
    private Double lastLatitude;

    @Column(name = "last_longitude")
    private Double lastLongitude;

    @Column(name = "last_speed_kmh")
    private Double lastSpeedKmh;

    @Column(name = "last_ping_at")
    private LocalDateTime lastPingAt;

    @Column(name = "odometer_km", nullable = false)
    private int odometerKm;

    @Version
    private long version;

    public boolean isOwnFleet() {
        return vendor == null;
    }

    public boolean isShuttle() {
        return vehicleType == VehicleType.SHUTTLE;
    }
}
