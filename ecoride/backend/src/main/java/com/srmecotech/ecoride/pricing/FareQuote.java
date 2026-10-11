package com.srmecotech.ecoride.pricing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** The upfront prices shown to a rider for one pickup and drop, held for QUOTE_TTL_S. */
@Entity
@Table(name = "fare_quote")
@Getter
@Setter
public class FareQuote {

    @Id
    private String id;

    private Long riderId;
    private double pickupLat;
    private double pickupLng;
    private String pickupAddress;
    private double dropLat;
    private double dropLng;
    private String dropAddress;
    @Column(name = "distance_m")
    private int distanceM;
    @Column(name = "duration_s")
    private int durationS;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String polyline;

    private String routeProvider;
    private BigDecimal surgeMultiplier;
    private String surgeZone;
    private String promoCode;

    @JdbcTypeCode(SqlTypes.JSON)
    private Options options;

    private Instant createdAt;
    private Instant expiresAt;

    public record Options(List<QuoteOption> items) {
    }

    /**
     * One product on the "Choose a ride" list.
     *
     * @param etaS          seconds for the nearest free driver of this type to reach the pickup; null if none nearby
     * @param driversNearby free drivers of this type within the dispatch radius
     */
    public record QuoteOption(String vehicleType, String name, String description, int capacity, FareBreakdown fare,
                              Integer etaS, int driversNearby) {
    }
}
