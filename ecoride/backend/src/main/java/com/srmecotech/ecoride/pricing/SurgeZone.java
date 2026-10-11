package com.srmecotech.ecoride.pricing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** A circle where surge applies: AUTO (demand vs free drivers) or MANUAL (a fixed multiplier set by ops). */
@Entity
@Table(name = "surge_zone")
@Getter
@Setter
public class SurgeZone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private double lat;
    private double lng;
    @Column(name = "radius_m")
    private int radiusM;
    private String mode;
    private BigDecimal manualMultiplier;
    private boolean active;
    private Instant updatedAt;
}
