package com.srmecotech.ecoride.driver;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A driver account (driver app) with the one vehicle it drives.
 * <p>
 * Position, availability, rating and offer counters change through atomic SQL updates in
 * {@link DriverRepository}, never by saving this entity: a GPS fix arrives every few
 * seconds, and @DynamicUpdate keeps a profile or status save from writing back stale values.
 */
@Entity
@Table(name = "driver")
@Getter
@Setter
@DynamicUpdate
public class Driver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String phone;
    private String email;
    private String passwordHash;
    private String licenseNo;

    private String vehicleType;
    private String vehicleMake;
    private String vehicleModel;
    private String vehicleColor;
    private String vehiclePlate;
    private int vehicleYear;

    @Enumerated(EnumType.STRING)
    private DriverStatus status;

    private String statusReason;

    @Enumerated(EnumType.STRING)
    private Availability availability;

    private BigDecimal ratingAvg;
    private int ratingCount;
    private int offersReceived;
    private int offersAccepted;

    private Double lat;
    private Double lng;
    private Integer heading;
    private Double speedKmh;
    private Instant locatedAt;
    private Instant onlineSince;

    private boolean simulated;
    private Instant createdAt;
    private Instant approvedAt;
    private Instant lastLoginAt;

    public String vehicleLabel() {
        return vehicleColor + " " + vehicleMake + " " + vehicleModel;
    }
}
