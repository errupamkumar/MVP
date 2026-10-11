package com.srmecotech.ecoride.pricing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** A ride product (Bike, Auto, Mini, Sedan, SUV) and its fare card. */
@Entity
@Table(name = "vehicle_type")
@Getter
@Setter
public class VehicleType {

    @Id
    private String code;

    private String name;
    private String description;
    private int capacity;

    /** Covers the first {@link #baseKm} kilometres. */
    @Column(name = "base_fare")
    private BigDecimal baseFare;

    @Column(name = "base_km")
    private BigDecimal baseKm;

    @Column(name = "per_km")
    private BigDecimal perKm;

    @Column(name = "per_min")
    private BigDecimal perMin;

    @Column(name = "min_fare")
    private BigDecimal minFare;

    /** Minutes a driver waits at the pickup free of charge. */
    @Column(name = "wait_free_min")
    private int waitFreeMin;

    @Column(name = "wait_per_min")
    private BigDecimal waitPerMin;

    @Column(name = "cancel_fee")
    private BigDecimal cancelFee;

    @Column(name = "sort_order")
    private int sortOrder;

    private boolean active;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
